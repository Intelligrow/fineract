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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.lead.domain.lead.Lead;
import org.apache.fineract.lead.domain.policy.LeadStageMakerCheckerPolicy;
import org.apache.fineract.lead.domain.stage.LeadStageAction;
import org.apache.fineract.lead.domain.stage.LeadStageCheck;
import org.apache.fineract.lead.domain.stage.LeadStageCheckDecision;
import org.apache.fineract.lead.domain.stage.LeadStageExecutionResult;
import org.apache.fineract.lead.domain.stage.LeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageInstance;
import org.apache.fineract.lead.domain.stage.LeadStageRegistry;
import org.apache.fineract.lead.domain.stage.LeadStageStatus;
import org.apache.fineract.lead.domain.stage.LeadStageSubmission;
import org.apache.fineract.lead.domain.stage.LeadStageSubmissionStatus;
import org.apache.fineract.lead.infrastructure.persistence.LeadRepository;
import org.apache.fineract.lead.infrastructure.persistence.LeadStageCheckRepository;
import org.apache.fineract.lead.infrastructure.persistence.LeadStageInstanceRepository;
import org.apache.fineract.lead.infrastructure.persistence.LeadStageSubmissionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class LeadWritePlatformServiceImpl implements LeadWritePlatformService {

    private final LeadRepository leadRepository;
    private final LeadStageInstanceRepository stageInstanceRepository;
    private final LeadStageSubmissionRepository submissionRepository;
    private final LeadStageCheckRepository checkRepository;
    private final LeadStageRegistry stageRegistry;
    private final LeadStageMakerCheckerPolicy makerCheckerPolicy;
    private final PlatformSecurityContext securityContext;

    @Override
    @Transactional
    public CommandProcessingResult create(JsonCommand command) {
        final JsonObject json = jsonObject(command);
        final Lead lead = Lead.create(stringValue(json, "externalId"), longValue(json, "officeId"), longValue(json, "assignedStaffId"));
        leadRepository.saveAndFlush(lead);
        return new CommandProcessingResultBuilder().withEntityId(lead.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult update(JsonCommand command) {
        final JsonObject json = jsonObject(command);
        final Lead lead = leadRepository.findById(command.entityId())
                .orElseThrow(() -> new IllegalArgumentException("Lead not found: " + command.entityId()));
        if (json.has("externalId")) {
            lead.setExternalId(stringValue(json, "externalId"));
        }
        if (json.has("officeId")) {
            lead.setOfficeId(longValue(json, "officeId"));
        }
        if (json.has("assignedStaffId")) {
            lead.setAssignedStaffId(longValue(json, "assignedStaffId"));
        }
        leadRepository.save(lead);
        return new CommandProcessingResultBuilder().withEntityId(lead.getId()).with(Map.of("updated", true)).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult processStageAction(JsonCommand command) {
        final JsonObject json = jsonObject(command);
        final LeadStageInstance stage = findStage(command.subentityId());
        stage.assertVersion(longValue(json, "version"));

        final LeadStageHandler handler = stageRegistry.getRequired(stage.getStageCode());
        final LeadStageAction action = actionValue(json, "action", LeadStageAction.SAVE);
        final boolean wasPendingCheck = stage.getStatus() == LeadStageStatus.PENDING_CHECK;
        final LeadStageExecutionResult result = handler.executeAction(action, null);
        if (!result.accepted()) {
            throw new IllegalArgumentException(result.message());
        }
        if (result.resultingStatus() != null) {
            stage.setStatus(result.resultingStatus());
        }
        if (action == LeadStageAction.SAVE) {
            stage.recordDataChange();
            if (wasPendingCheck) {
                submissionRepository.findFirstByStageInstanceIdAndStatusOrderByIdDesc(stage.getId(), LeadStageSubmissionStatus.PENDING)
                        .ifPresent(submission -> {
                            submission.supersede();
                            submissionRepository.save(submission);
                        });
            }
        }
        stageInstanceRepository.save(stage);
        return new CommandProcessingResultBuilder().withEntityId(command.entityId()).withSubEntityId(stage.getId())
                .with(Map.of("stageStatus", stage.getStatus().name())).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult submitStage(JsonCommand command) {
        final JsonObject json = jsonObject(command);
        final LeadStageInstance stage = findStage(command.subentityId());
        stage.assertVersion(longValue(json, "version"));
        stage.submitForCheck();
        final LeadStageSubmission submission = submissionRepository
                .save(LeadStageSubmission.pending(stage.getId(), actingUserId(json), stage.getRevision()));
        stageInstanceRepository.save(stage);
        return new CommandProcessingResultBuilder().withEntityId(command.entityId()).withSubEntityId(stage.getId())
                .with(Map.of("submissionId", submission.getId(), "revision", submission.getRevision())).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult checkStage(JsonCommand command) {
        final JsonObject json = jsonObject(command);
        final LeadStageInstance stage = findStage(command.subentityId());
        final LeadStageSubmission submission = submissionRepository
                .findFirstByStageInstanceIdAndStatusOrderByIdDesc(stage.getId(), LeadStageSubmissionStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("No pending submission exists for lead stage: " + stage.getId()));
        final long checkedRevision = longValue(json, "revision");
        final var validation = makerCheckerPolicy.validateCheck(submission.getMakerUserId(), actingUserId(json), submission.getRevision(),
                checkedRevision, stage.getRevision());
        if (!validation.isValid()) {
            throw new IllegalArgumentException(validation.errors().get(0).message());
        }
        final LeadStageCheckDecision decision = decisionValue(json, "decision", LeadStageCheckDecision.APPROVED);
        checkRepository.save(
                LeadStageCheck.record(submission.getId(), actingUserId(json), checkedRevision, decision, stringValue(json, "remarks")));
        if (decision == LeadStageCheckDecision.APPROVED) {
            submission.approve();
            stage.complete();
        } else {
            submission.returnForRework();
            stage.returnForRework();
        }
        submissionRepository.save(submission);
        stageInstanceRepository.save(stage);
        return new CommandProcessingResultBuilder().withEntityId(command.entityId()).withSubEntityId(stage.getId())
                .with(Map.of("decision", decision.name())).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult skipStage(JsonCommand command) {
        final JsonObject json = jsonObject(command);
        final LeadStageInstance stage = findStage(command.subentityId());
        stage.assertVersion(longValue(json, "version"));
        stage.skip(stringValue(json, "reason"));
        stageInstanceRepository.save(stage);
        return new CommandProcessingResultBuilder().withEntityId(command.entityId()).withSubEntityId(stage.getId())
                .with(Map.of("stageStatus", stage.getStatus().name())).build();
    }

    private LeadStageInstance findStage(Long stageInstanceId) {
        return stageInstanceRepository.findById(stageInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Lead stage not found: " + stageInstanceId));
    }

    private Long actingUserId(JsonObject json) {
        final Long explicitActor = longValue(json, "actorUserId");
        if (explicitActor != null) {
            return explicitActor;
        }
        final var user = securityContext.getAuthenticatedUserIfPresent();
        return user == null ? null : user.getId();
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

    private static Long longValue(JsonObject json, String name) {
        final JsonElement value = json.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsLong();
    }

    private static LeadStageAction actionValue(JsonObject json, String name, LeadStageAction defaultValue) {
        final String value = stringValue(json, name);
        return StringUtils.isBlank(value) ? defaultValue : LeadStageAction.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private static LeadStageCheckDecision decisionValue(JsonObject json, String name, LeadStageCheckDecision defaultValue) {
        final String value = stringValue(json, name);
        return StringUtils.isBlank(value) ? defaultValue : LeadStageCheckDecision.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
