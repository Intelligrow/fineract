/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.lead.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.lead.domain.process.LeadProcessDefinition;
import org.apache.fineract.lead.domain.process.LeadProcessDefinitionVersion;
import org.apache.fineract.lead.domain.process.LeadProcessStageConfig;
import org.apache.fineract.lead.domain.stage.LeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageRegistry;
import org.apache.fineract.lead.domain.stage.StageCapability;
import org.apache.fineract.lead.exception.LeadStageRuleViolationException;
import org.apache.fineract.lead.infrastructure.persistence.LeadProcessDefinitionRepository;
import org.apache.fineract.lead.infrastructure.persistence.LeadProcessDefinitionVersionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class LeadProcessWritePlatformServiceImpl implements LeadProcessWritePlatformService {

    private static final int FIRST_VERSION_NUMBER = 1;

    private final LeadStageRegistry stageRegistry;
    private final LeadProcessDefinitionRepository processDefinitionRepository;
    private final LeadProcessDefinitionVersionRepository processDefinitionVersionRepository;

    @Override
    @Transactional
    public CommandProcessingResult configureProcess(JsonCommand command) {
        if (command.entityId() == null) {
            return createDraftProcess(command);
        }
        if (command.subentityId() == null) {
            return createNextDraftVersion(command);
        }
        return replaceStageConfigurations(command);
    }

    @Override
    @Transactional
    public CommandProcessingResult publishProcess(JsonCommand command) {
        final LeadProcessDefinitionVersion version = findVersion(command.entityId(), command.subentityId());
        validateReadyForPublication(version);
        version.publish();
        processDefinitionVersionRepository.saveAndFlush(version);
        return new CommandProcessingResultBuilder().withEntityId(command.entityId()).withSubEntityId(version.getId())
                .with(java.util.Map.of("published", true, "versionNumber", version.getVersionNumber())).build();
    }

    private CommandProcessingResult createDraftProcess(JsonCommand command) {
        final JsonObject json = jsonObject(command);
        final String name = stringValue(json, "name");
        if (StringUtils.isBlank(name)) {
            throw new LeadStageRuleViolationException("Lead process definition name is required.");
        }
        final LeadProcessDefinition definition = LeadProcessDefinition.create(name.trim(), stringValue(json, "description"));
        processDefinitionRepository.saveAndFlush(definition);

        final LeadProcessDefinitionVersion version = LeadProcessDefinitionVersion.draft(definition.getId(), FIRST_VERSION_NUMBER);
        processDefinitionVersionRepository.saveAndFlush(version);

        return new CommandProcessingResultBuilder().withEntityId(definition.getId()).withSubEntityId(version.getId())
                .with(java.util.Map.of("versionNumber", version.getVersionNumber())).build();
    }

    private CommandProcessingResult replaceStageConfigurations(JsonCommand command) {
        final LeadProcessDefinitionVersion version = findVersion(command.entityId(), command.subentityId());
        version.assertMutable();
        final List<LeadProcessStageConfig> stageConfigurations = parseStageConfigurations(jsonObject(command));
        version.replaceStageConfigurations(stageConfigurations);
        processDefinitionVersionRepository.saveAndFlush(version);
        return new CommandProcessingResultBuilder().withEntityId(command.entityId()).withSubEntityId(version.getId())
                .with(java.util.Map.of("stageCount", version.getStageConfigurations().size())).build();
    }

    private CommandProcessingResult createNextDraftVersion(JsonCommand command) {
        processDefinitionRepository.findById(command.entityId())
                .orElseThrow(() -> new IllegalArgumentException("Lead process definition not found: " + command.entityId()));
        final List<LeadProcessDefinitionVersion> existingVersions = processDefinitionVersionRepository
                .findByProcessDefinitionIdOrderByVersionNumberDesc(command.entityId());
        existingVersions.stream().filter(version -> !version.isPublished()).findAny().ifPresent(version -> {
            throw new LeadStageRuleViolationException("A draft lead process definition version already exists: " + version.getId());
        });
        final int nextVersionNumber = existingVersions.stream().map(LeadProcessDefinitionVersion::getVersionNumber).max(Integer::compareTo)
                .orElse(0) + 1;
        final LeadProcessDefinitionVersion draft = LeadProcessDefinitionVersion.draft(command.entityId(), nextVersionNumber);
        existingVersions.stream().findFirst().ifPresent(latestVersion -> draft
                .replaceStageConfigurations(latestVersion.getStageConfigurations().stream().map(LeadProcessStageConfig::copyOf).toList()));
        processDefinitionVersionRepository.saveAndFlush(draft);
        return new CommandProcessingResultBuilder().withEntityId(command.entityId()).withSubEntityId(draft.getId())
                .with(java.util.Map.of("versionNumber", draft.getVersionNumber())).build();
    }

    private List<LeadProcessStageConfig> parseStageConfigurations(JsonObject json) {
        final JsonArray stages = json.has("stages") && json.get("stages").isJsonArray() ? json.getAsJsonArray("stages") : new JsonArray();
        final Set<String> stageCodes = new LinkedHashSet<>();
        final Set<Integer> sequences = new LinkedHashSet<>();
        final java.util.ArrayList<LeadProcessStageConfig> configs = new java.util.ArrayList<>();
        for (int index = 0; index < stages.size(); index++) {
            final JsonObject stageJson = stages.get(index).getAsJsonObject();
            final LeadStageHandler handler = stageRegistry.getRequired(stringValue(stageJson, "stageCode"));
            final String stageCode = handler.stageCode().trim().toUpperCase(Locale.ROOT);
            if (!stageCodes.add(stageCode)) {
                throw new LeadStageRuleViolationException("Duplicate lead stage code in process definition version: " + stageCode);
            }
            final int sequence = intValue(stageJson, "sequence", index + 1);
            if (sequence <= 0) {
                throw new LeadStageRuleViolationException("Lead stage sequence must be positive.");
            }
            if (!sequences.add(sequence)) {
                throw new LeadStageRuleViolationException("Duplicate lead stage sequence in process definition version: " + sequence);
            }
            final boolean enabled = booleanValue(stageJson, "enabled", true);
            final boolean mandatory = booleanValue(stageJson, "mandatory", false);
            final boolean skippable = booleanValue(stageJson, "skippable", false);
            final boolean makerCheckerRequired = booleanValue(stageJson, "makerCheckerRequired", false);
            final boolean autoPrefill = booleanValue(stageJson, "autoPrefill", false);
            final boolean manualFallback = booleanValue(stageJson, "manualFallback", false);
            final Set<String> requiredDatatables = stringSetValue(stageJson, "requiredDatatables");
            final String documentRequirementsJson = jsonFragment(stageJson, "documentRequirements");
            validateStageConfiguration(handler, stageCode, mandatory, skippable, makerCheckerRequired, autoPrefill, manualFallback,
                    !requiredDatatables.isEmpty(), hasDocumentConfiguration(stageJson));
            configs.add(LeadProcessStageConfig.create(stageCode, sequence, enabled, mandatory, skippable, makerCheckerRequired, autoPrefill,
                    manualFallback, longValue(stageJson, "assignedRoleId"), integerValue(stageJson, "slaHours"), requiredDatatables,
                    documentRequirementsJson, jsonFragment(stageJson, "options")));
        }
        configs.sort(java.util.Comparator.comparingInt(LeadProcessStageConfig::getSequence));
        return configs;
    }

    private static void validateStageConfiguration(LeadStageHandler handler, String stageCode, boolean mandatory, boolean skippable,
            boolean makerCheckerRequired, boolean autoPrefill, boolean manualFallback, boolean hasDatatableConfiguration,
            boolean hasDocumentConfiguration) {
        if (mandatory && skippable) {
            throw new LeadStageRuleViolationException("Mandatory lead stages cannot be skippable: " + stageCode);
        }
        validateCapability(handler, stageCode, makerCheckerRequired, StageCapability.MAKER_CHECKER, "makerCheckerRequired");
        validateCapability(handler, stageCode, autoPrefill, StageCapability.EXTERNAL_PREFILL, "autoPrefill");
        validateCapability(handler, stageCode, manualFallback, StageCapability.MANUAL_FALLBACK, "manualFallback");
        validateCapability(handler, stageCode, hasDocumentConfiguration, StageCapability.DOCUMENTS, "documentRequirements");
        validateCapability(handler, stageCode, hasDatatableConfiguration, StageCapability.DATATABLES, "requiredDatatables");
    }

    private static void validateCapability(LeadStageHandler handler, String stageCode, boolean required, StageCapability capability,
            String fieldName) {
        if (required && !handler.capabilities().contains(capability)) {
            throw new LeadStageRuleViolationException("Lead stage " + stageCode + " does not support " + fieldName + " configuration.");
        }
    }

    private static void validateReadyForPublication(LeadProcessDefinitionVersion version) {
        if (version.getStageConfigurations().stream().noneMatch(LeadProcessStageConfig::isEnabled)) {
            throw new LeadStageRuleViolationException("At least one enabled lead stage is required before publication.");
        }
    }

    private LeadProcessDefinitionVersion findVersion(Long processDefinitionId, Long versionId) {
        if (processDefinitionId == null || versionId == null) {
            throw new IllegalArgumentException("Lead process definition id and version id are required.");
        }
        final LeadProcessDefinitionVersion version = processDefinitionVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Lead process definition version not found: " + versionId));
        if (!processDefinitionId.equals(version.getProcessDefinitionId())) {
            throw new IllegalArgumentException(
                    "Lead process definition version does not belong to process definition: " + processDefinitionId);
        }
        return version;
    }

    private static JsonObject jsonObject(JsonCommand command) {
        final JsonElement parsed = command.parsedJson();
        if (parsed != null && parsed.isJsonObject()) {
            return parsed.getAsJsonObject();
        }
        if (StringUtils.isBlank(command.json())) {
            return new JsonObject();
        }
        return JsonParser.parseString(command.json()).getAsJsonObject();
    }

    private static String stringValue(JsonObject json, String name) {
        final JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static Integer integerValue(JsonObject json, String name) {
        final JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsInt();
    }

    private static int intValue(JsonObject json, String name, int defaultValue) {
        final Integer value = integerValue(json, name);
        return value == null ? defaultValue : value;
    }

    private static Long longValue(JsonObject json, String name) {
        final JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsLong();
    }

    private static boolean booleanValue(JsonObject json, String name, boolean defaultValue) {
        final JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? defaultValue : value.getAsBoolean();
    }

    private static Set<String> stringSetValue(JsonObject json, String name) {
        final JsonElement value = json.get(name);
        if (value == null || value.isJsonNull()) {
            return Set.of();
        }
        final Set<String> values = new LinkedHashSet<>();
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(item -> addStringValue(values, item.getAsString()));
        } else {
            for (String item : value.getAsString().split(",")) {
                addStringValue(values, item);
            }
        }
        return Set.copyOf(values);
    }

    private static void addStringValue(Set<String> values, String value) {
        if (StringUtils.isNotBlank(value)) {
            values.add(value.trim());
        }
    }

    private static String jsonFragment(JsonObject json, String name) {
        final JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? null : value.toString();
    }

    private static boolean hasDocumentConfiguration(JsonObject json) {
        final JsonElement value = json.get("documentRequirements");
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonArray()) {
            return !value.getAsJsonArray().isEmpty();
        }
        if (value.isJsonObject()) {
            return !value.getAsJsonObject().entrySet().isEmpty();
        }
        return StringUtils.isNotBlank(value.getAsString());
    }
}
