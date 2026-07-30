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
package org.apache.fineract.lead.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.lead.data.AvailableLeadStageData;
import org.apache.fineract.lead.domain.process.LeadProcessDefinition;
import org.apache.fineract.lead.domain.process.LeadProcessDefinitionVersion;
import org.apache.fineract.lead.domain.process.LeadProcessStageConfig;
import org.apache.fineract.lead.domain.stage.AbstractLeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageAction;
import org.apache.fineract.lead.domain.stage.LeadStageContext;
import org.apache.fineract.lead.domain.stage.LeadStageExecutionResult;
import org.apache.fineract.lead.domain.stage.LeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageRegistry;
import org.apache.fineract.lead.domain.stage.LeadStageStatus;
import org.apache.fineract.lead.domain.stage.StageCapability;
import org.apache.fineract.lead.exception.LeadProcessDefinitionImmutableException;
import org.apache.fineract.lead.exception.LeadStageHandlerNotFoundException;
import org.apache.fineract.lead.exception.LeadStageRuleViolationException;
import org.apache.fineract.lead.infrastructure.persistence.LeadProcessDefinitionRepository;
import org.apache.fineract.lead.infrastructure.persistence.LeadProcessDefinitionVersionRepository;
import org.apache.fineract.lead.service.LeadProcessReadPlatformServiceImpl;
import org.apache.fineract.lead.service.LeadProcessWritePlatformServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadProcessConfigurationTest {

    @Mock
    private LeadProcessDefinitionRepository processDefinitionRepository;

    @Mock
    private LeadProcessDefinitionVersionRepository processDefinitionVersionRepository;

    private LeadStageRegistry stageRegistry;
    private LeadProcessWritePlatformServiceImpl writeService;
    private LeadProcessReadPlatformServiceImpl readService;

    @BeforeEach
    void setUp() {
        stageRegistry = new LeadStageRegistry(List.of(
                handler("BASIC_DETAILS", StageCapability.MANUAL_DATA, StageCapability.DATATABLES, StageCapability.DOCUMENTS,
                        StageCapability.MAKER_CHECKER),
                handler("CREDIT_BUREAU", StageCapability.EXTERNAL_PREFILL, StageCapability.DATATABLES, StageCapability.MANUAL_FALLBACK),
                handler("MANUAL_ONLY", StageCapability.MANUAL_DATA)));
        writeService = new LeadProcessWritePlatformServiceImpl(stageRegistry, processDefinitionRepository,
                processDefinitionVersionRepository);
        readService = new LeadProcessReadPlatformServiceImpl(stageRegistry, processDefinitionRepository,
                processDefinitionVersionRepository);
    }

    @Test
    void listsAvailableStageHandlersFromRegistry() {
        final List<AvailableLeadStageData> availableStages = readService.retrieveAvailableStages();

        assertThat(availableStages).extracting(AvailableLeadStageData::stageCode).containsExactly("BASIC_DETAILS", "CREDIT_BUREAU",
                "MANUAL_ONLY");
        assertThat(availableStages.get(0).capabilities()).contains(StageCapability.MANUAL_DATA);
    }

    @Test
    void createsDraftProcessDefinitionWithVersionOne() {
        when(processDefinitionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            final LeadProcessDefinition definition = invocation.getArgument(0);
            definition.setId(11L);
            return definition;
        });
        when(processDefinitionVersionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            final LeadProcessDefinitionVersion version = invocation.getArgument(0);
            version.setId(21L);
            return version;
        });

        final CommandProcessingResult result = writeService
                .configureProcess(command(null, null, "{\"name\":\"Retail Lead Process\",\"description\":\"Retail flow\"}"));

        assertThat(result.getResourceId()).isEqualTo(11L);
        assertThat(result.getSubResourceId()).isEqualTo(21L);
        final ArgumentCaptor<LeadProcessDefinitionVersion> versionCaptor = ArgumentCaptor
                .forClass(LeadProcessDefinitionVersion.class);
        verify(processDefinitionVersionRepository).saveAndFlush(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getProcessDefinitionId()).isEqualTo(11L);
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(versionCaptor.getValue().isPublished()).isFalse();
    }

    @Test
    void addOrUpdateOrderedStageConfigurationsByStableStageCode() {
        final LeadProcessDefinitionVersion version = draftVersion();
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));
        when(processDefinitionVersionRepository.saveAndFlush(version)).thenReturn(version);

        final CommandProcessingResult result = writeService.configureProcess(command(11L, 21L, """
                {
                  "stages": [
                    {"stageCode": "credit_bureau", "sequence": 2, "enabled": true, "mandatory": false, "skippable": true,
                     "requiredDatatables": ["lead_credit_bureau_prefill"], "autoPrefill": true, "manualFallback": true,
                     "options": {"provider": "fake"}},
                    {"stageCode": "BASIC_DETAILS", "sequence": 1, "enabled": true, "mandatory": true, "makerCheckerRequired": true}
                  ]
                }
                """));

        assertThat(result.getSubResourceId()).isEqualTo(21L);
        assertThat(version.getStageConfigurations()).extracting(LeadProcessStageConfig::getStageCode).containsExactly("BASIC_DETAILS",
                "CREDIT_BUREAU");
        assertThat(version.getStageConfigurations().get(1).getRequiredDatatables()).isEqualTo("lead_credit_bureau_prefill");
        assertThat(version.getStageConfigurations().get(1).isAutoPrefill()).isTrue();
        assertThat(version.getStageConfigurations().get(1).isManualFallback()).isTrue();
        assertThat(version.getStageConfigurations().get(1).getOptionsJson()).isEqualTo("{\"provider\":\"fake\"}");
    }

    @Test
    void rejectsUnknownStageCodes() {
        final LeadProcessDefinitionVersion version = draftVersion();
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThatThrownBy(
                () -> writeService.configureProcess(command(11L, 21L, "{\"stages\":[{\"stageCode\":\"UNKNOWN\",\"sequence\":1}]}")))
                .isInstanceOf(LeadStageHandlerNotFoundException.class);
    }

    @Test
    void rejectsDuplicateStageCodesInOneDefinitionVersion() {
        final LeadProcessDefinitionVersion version = draftVersion();
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> writeService.configureProcess(command(11L, 21L,
                "{\"stages\":[{\"stageCode\":\"BASIC_DETAILS\",\"sequence\":1},{\"stageCode\":\"basic_details\",\"sequence\":2}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("Duplicate lead stage code");
    }

    @Test
    void rejectsDuplicateSequenceValuesBeforePersistence() {
        final LeadProcessDefinitionVersion version = draftVersion();
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> writeService.configureProcess(command(11L, 21L,
                "{\"stages\":[{\"stageCode\":\"BASIC_DETAILS\",\"sequence\":1},{\"stageCode\":\"CREDIT_BUREAU\",\"sequence\":1}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("Duplicate lead stage sequence");
        verify(processDefinitionVersionRepository, never()).saveAndFlush(version);
        assertThat(version.getStageConfigurations()).isEmpty();
    }

    @Test
    void rejectsNonPositiveSequenceValues() {
        final LeadProcessDefinitionVersion version = draftVersion();
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThatThrownBy(
                () -> writeService.configureProcess(command(11L, 21L, "{\"stages\":[{\"stageCode\":\"BASIC_DETAILS\",\"sequence\":0}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("sequence must be positive");
    }

    @Test
    void rejectsMandatoryStageThatIsSkippable() {
        final LeadProcessDefinitionVersion version = draftVersion();
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> writeService.configureProcess(
                command(11L, 21L, "{\"stages\":[{\"stageCode\":\"BASIC_DETAILS\",\"sequence\":1,\"mandatory\":true,\"skippable\":true}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("cannot be skippable");
    }

    @Test
    void rejectsCapabilityConfigurationWhenHandlerDoesNotSupportIt() {
        final LeadProcessDefinitionVersion version = draftVersion();
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> writeService.configureProcess(
                command(11L, 21L, "{\"stages\":[{\"stageCode\":\"BASIC_DETAILS\",\"sequence\":1,\"autoPrefill\":true}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("autoPrefill");
        assertThatThrownBy(() -> writeService.configureProcess(
                command(11L, 21L, "{\"stages\":[{\"stageCode\":\"BASIC_DETAILS\",\"sequence\":1,\"manualFallback\":true}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("manualFallback");
        assertThatThrownBy(() -> writeService.configureProcess(
                command(11L, 21L, "{\"stages\":[{\"stageCode\":\"CREDIT_BUREAU\",\"sequence\":1,\"makerCheckerRequired\":true}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("makerCheckerRequired");
        assertThatThrownBy(() -> writeService.configureProcess(command(11L, 21L,
                "{\"stages\":[{\"stageCode\":\"CREDIT_BUREAU\",\"sequence\":1,\"documentRequirements\":[{\"category\":\"ID\"}]}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("documentRequirements");
        assertThatThrownBy(() -> writeService.configureProcess(command(11L, 21L,
                "{\"stages\":[{\"stageCode\":\"MANUAL_ONLY\",\"sequence\":1,\"requiredDatatables\":[\"lead_manual\"]}]}")))
                .isInstanceOf(LeadStageRuleViolationException.class).hasMessageContaining("requiredDatatables");
    }

    @Test
    void publishesProcessDefinitionVersion() {
        final LeadProcessDefinitionVersion version = draftVersion();
        version.addStageConfiguration(
                LeadProcessStageConfig.create("BASIC_DETAILS", 1, true, true, false, true, false, false, null, null, Set.of(), null, null));
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));
        when(processDefinitionVersionRepository.saveAndFlush(version)).thenReturn(version);

        final CommandProcessingResult result = writeService.publishProcess(command(11L, 21L, "{}"));

        assertThat(result.getSubResourceId()).isEqualTo(21L);
        assertThat(version.isPublished()).isTrue();
        assertThat(version.getPublishedOn()).isNotNull();
    }

    @Test
    void rejectsPublicationWithoutEnabledStage() {
        final LeadProcessDefinitionVersion version = draftVersion();
        version.addStageConfiguration(LeadProcessStageConfig.create("BASIC_DETAILS", 1, false, true, false, true, false, false, null, null,
                Set.of(), null, null));
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> writeService.publishProcess(command(11L, 21L, "{}"))).isInstanceOf(LeadStageRuleViolationException.class)
                .hasMessageContaining("At least one enabled lead stage");
    }

    @Test
    void rejectsMutationAfterPublish() {
        final LeadProcessDefinitionVersion version = draftVersion();
        version.addStageConfiguration(
                LeadProcessStageConfig.create("BASIC_DETAILS", 1, true, true, false, true, false, false, null, null, Set.of(), null, null));
        version.publish();
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThatThrownBy(
                () -> writeService.configureProcess(command(11L, 21L, "{\"stages\":[{\"stageCode\":\"BASIC_DETAILS\",\"sequence\":1}]}")))
                .isInstanceOf(LeadProcessDefinitionImmutableException.class);
    }

    @Test
    void createsNewDraftVersionAfterPublicationInsteadOfReopeningPublishedVersion() {
        final LeadProcessDefinition definition = LeadProcessDefinition.create("Retail Process", "Retail");
        definition.setId(11L);
        final LeadProcessDefinitionVersion publishedVersion = draftVersion();
        publishedVersion.addStageConfiguration(
                LeadProcessStageConfig.create("BASIC_DETAILS", 1, true, true, false, true, false, false, null, null, Set.of(), null, null));
        publishedVersion.publish();
        when(processDefinitionRepository.findById(11L)).thenReturn(Optional.of(definition));
        when(processDefinitionVersionRepository.findByProcessDefinitionIdOrderByVersionNumberDesc(11L))
                .thenReturn(List.of(publishedVersion));
        when(processDefinitionVersionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            final LeadProcessDefinitionVersion draft = invocation.getArgument(0);
            draft.setId(22L);
            return draft;
        });

        final CommandProcessingResult result = writeService.configureProcess(command(11L, null, "{}"));

        assertThat(result.getSubResourceId()).isEqualTo(22L);
        final ArgumentCaptor<LeadProcessDefinitionVersion> draftCaptor = ArgumentCaptor.forClass(LeadProcessDefinitionVersion.class);
        verify(processDefinitionVersionRepository).saveAndFlush(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getVersionNumber()).isEqualTo(2);
        assertThat(draftCaptor.getValue().isPublished()).isFalse();
        assertThat(draftCaptor.getValue().getStageConfigurations()).extracting(LeadProcessStageConfig::getStageCode)
                .containsExactly("BASIC_DETAILS");
        assertThat(publishedVersion.isPublished()).isTrue();
    }

    @Test
    void rejectsNewDraftVersionWhenDraftAlreadyExists() {
        final LeadProcessDefinition definition = LeadProcessDefinition.create("Retail Process", "Retail");
        definition.setId(11L);
        final LeadProcessDefinitionVersion draftVersion = draftVersion();
        when(processDefinitionRepository.findById(11L)).thenReturn(Optional.of(definition));
        when(processDefinitionVersionRepository.findByProcessDefinitionIdOrderByVersionNumberDesc(11L)).thenReturn(List.of(draftVersion));

        assertThatThrownBy(() -> writeService.configureProcess(command(11L, null, "{}")))
                .isInstanceOf(LeadStageRuleViolationException.class)
                .hasMessageContaining("draft lead process definition version already exists");
    }

    @Test
    void retrievesProcessDefinitionsAndVersions() {
        final LeadProcessDefinition definition = LeadProcessDefinition.create("Retail Process", "Retail");
        definition.setId(11L);
        final LeadProcessDefinitionVersion version = draftVersion();
        version.addStageConfiguration(
                LeadProcessStageConfig.create("BASIC_DETAILS", 1, true, true, false, true, false, false, null, null, Set.of(), null, null));

        when(processDefinitionRepository.findAll()).thenReturn(List.of(definition));
        when(processDefinitionRepository.findById(11L)).thenReturn(Optional.of(definition));
        when(processDefinitionVersionRepository.findByProcessDefinitionIdOrderByVersionNumberDesc(11L)).thenReturn(List.of(version));
        when(processDefinitionVersionRepository.findById(21L)).thenReturn(Optional.of(version));

        assertThat(readService.retrieveAllProcessDefinitions()).extracting("name").containsExactly("Retail Process");
        assertThat(readService.retrieveProcessDefinitionVersions(11L)).hasSize(1);
        assertThat(readService.retrieveProcessDefinitionVersion(11L, 21L).stages()).hasSize(1);
    }

    private static LeadProcessDefinitionVersion draftVersion() {
        final LeadProcessDefinitionVersion version = LeadProcessDefinitionVersion.draft(11L, 1);
        version.setId(21L);
        return version;
    }

    private static JsonCommand command(Long resourceId, Long subresourceId, String json) {
        final JsonElement parsed = JsonParser.parseString(json);
        return JsonCommand.from(json, parsed, null, null, resourceId, subresourceId, null, null, null, null, null, null, null, null, null,
                null, null);
    }

    private static LeadStageHandler handler(String stageCode, StageCapability... capabilities) {
        return new AbstractLeadStageHandler(List.of(), null, null, null) {

            @Override
            public String stageCode() {
                return stageCode;
            }

            @Override
            public Set<StageCapability> capabilities() {
                return Set.of(capabilities);
            }

            @Override
            public LeadStageExecutionResult executeAction(LeadStageAction action, LeadStageContext context) {
                return LeadStageExecutionResult.accepted(LeadStageStatus.IN_PROGRESS, "ok");
            }
        };
    }
}
