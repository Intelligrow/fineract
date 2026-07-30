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
package org.apache.fineract.lead.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.lead.domain.document.LeadStageDocument;
import org.apache.fineract.lead.domain.document.LeadStageDocumentRequirement;
import org.apache.fineract.lead.domain.document.StageDocumentUpload;
import org.apache.fineract.lead.domain.policy.DefaultLeadStageMakerCheckerPolicy;
import org.apache.fineract.lead.domain.policy.LeadStageMakerCheckerPolicy;
import org.apache.fineract.lead.domain.process.LeadProcessDefinitionVersion;
import org.apache.fineract.lead.domain.process.LeadProcessInstance;
import org.apache.fineract.lead.domain.process.LeadProcessStageConfig;
import org.apache.fineract.lead.domain.stage.AbstractLeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageAction;
import org.apache.fineract.lead.domain.stage.LeadStageContext;
import org.apache.fineract.lead.domain.stage.LeadStageExecutionResult;
import org.apache.fineract.lead.domain.stage.LeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageInstance;
import org.apache.fineract.lead.domain.stage.LeadStagePrefillResult;
import org.apache.fineract.lead.domain.stage.LeadStagePrefillStatus;
import org.apache.fineract.lead.domain.stage.LeadStageRegistry;
import org.apache.fineract.lead.domain.stage.LeadStageStatus;
import org.apache.fineract.lead.domain.stage.LeadStageValidationResult;
import org.apache.fineract.lead.domain.stage.StageCapability;
import org.apache.fineract.lead.domain.stage.StageConfiguration;
import org.apache.fineract.lead.domain.stage.port.FineractDatatablePort;
import org.apache.fineract.lead.domain.stage.port.FineractDocumentPort;
import org.apache.fineract.lead.exception.LeadProcessDefinitionImmutableException;
import org.apache.fineract.lead.exception.LeadStageConcurrencyException;
import org.apache.fineract.lead.exception.LeadStageHandlerNotFoundException;
import org.apache.fineract.lead.exception.LeadStageRuleViolationException;
import org.apache.fineract.lead.infrastructure.integration.FakeCreditBureauPrefillProvider;
import org.apache.fineract.lead.stages.basicdetails.BasicDetailsStageHandler;
import org.apache.fineract.lead.stages.creditbureau.CreditBureauStageHandler;
import org.junit.jupiter.api.Test;

class LeadStageArchitectureTest {

    private final LeadStageMakerCheckerPolicy makerCheckerPolicy = new DefaultLeadStageMakerCheckerPolicy();

    @Test
    void registryResolvesAValidHandler() {
        final LeadStageRegistry registry = new LeadStageRegistry(List.of(basicDetailsHandler()));

        assertThat(registry.getRequired("basic_details").stageCode()).isEqualTo(BasicDetailsStageHandler.STAGE_CODE);
    }

    @Test
    void duplicateStageCodesFail() {
        assertThatThrownBy(() -> new LeadStageRegistry(List.of(dummy("DUPLICATE"), dummy(" duplicate "))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate stage code");
    }

    @Test
    void unknownStageCodeFailsSafely() {
        final LeadStageRegistry registry = new LeadStageRegistry(List.of(basicDetailsHandler()));

        assertThatThrownBy(() -> registry.getRequired("UNKNOWN")).isInstanceOf(LeadStageHandlerNotFoundException.class);
    }

    @Test
    void basicDetailsDeclaresExpectedCapabilities() {
        assertThat(basicDetailsHandler().capabilities()).containsExactlyInAnyOrder(StageCapability.MANUAL_DATA, StageCapability.DATATABLES,
                StageCapability.DOCUMENTS, StageCapability.MAKER_CHECKER);
    }

    @Test
    void fakeCreditBureauPrefillWritesThroughDatatablePortAndHonorsIdempotency() {
        final InMemoryDatatablePort datatablePort = new InMemoryDatatablePort();
        final FakeCreditBureauPrefillProvider provider = new FakeCreditBureauPrefillProvider();
        final CreditBureauStageHandler handler = new CreditBureauStageHandler(List.of(), datatablePort, new NoopDocumentPort(),
                makerCheckerPolicy, provider);
        final LeadStageContext context = creditBureauContext("prefill-key-1", false);

        final LeadStagePrefillResult firstResult = handler.prefill(context);
        final LeadStagePrefillResult replayResult = handler.prefill(context);

        assertThat(firstResult.status()).isEqualTo(LeadStagePrefillStatus.COMPLETED);
        assertThat(replayResult.status()).isEqualTo(LeadStagePrefillStatus.IDEMPOTENT_REPLAY);
        assertThat(datatablePort.readStageData(99L, CreditBureauStageHandler.DEFAULT_DATATABLE_NAME)).containsEntry("bureauScore", 742)
                .containsEntry("source", "FAKE_CREDIT_BUREAU");
        assertThat(datatablePort.writeCount()).isEqualTo(1);
        assertThat(provider.fakeExecutionCount()).isEqualTo(1);
    }

    @Test
    void fakeCreditBureauPrefillFailureDoesNotWriteDatatableData() {
        final InMemoryDatatablePort datatablePort = new InMemoryDatatablePort();
        final FakeCreditBureauPrefillProvider provider = new FakeCreditBureauPrefillProvider();
        final CreditBureauStageHandler handler = new CreditBureauStageHandler(List.of(), datatablePort, new NoopDocumentPort(),
                makerCheckerPolicy, provider);

        final LeadStagePrefillResult result = handler.prefill(creditBureauContext("prefill-key-2", true));

        assertThat(result.status()).isEqualTo(LeadStagePrefillStatus.FAILED);
        assertThat(datatablePort.writeCount()).isZero();
    }

    @Test
    void validationResultsMergeCorrectly() {
        final LeadStageValidationResult merged = LeadStageValidationResult.merge(
                LeadStageValidationResult.invalid("first", "First failure"), LeadStageValidationResult.valid(),
                LeadStageValidationResult.invalid("second", "Second failure", false));

        assertThat(merged.isValid()).isFalse();
        assertThat(merged.errors()).extracting("code").containsExactly("first", "second");
    }

    @Test
    void makerCannotCheckTheirOwnSubmission() {
        final LeadStageValidationResult result = makerCheckerPolicy.validateCheck(7L, 7L, 3L, 3L, 3L);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).extracting("code").contains("lead.stage.checker.same.user");
    }

    @Test
    void disabledStageIsOmittedFromANewProcess() {
        final LeadProcessDefinitionVersion definitionVersion = LeadProcessDefinitionVersion.draft(1L, 1);
        definitionVersion.setId(10L);
        definitionVersion.addStageConfiguration(LeadProcessStageConfig.from(StageConfiguration.enabled("BASIC_DETAILS", 1, true, false)));
        definitionVersion.addStageConfiguration(LeadProcessStageConfig
                .from(new StageConfiguration("CREDIT_BUREAU", false, 2, false, true, false, Set.of(), Set.of(), false, false, Map.of())));

        final LeadProcessInstance instance = LeadProcessInstance.start(22L, definitionVersion);

        assertThat(instance.getStageInstances()).extracting(LeadStageInstance::getStageCode).containsExactly("BASIC_DETAILS");
    }

    @Test
    void mandatoryStageCannotBeSkipped() {
        final LeadStageInstance stage = LeadStageInstance
                .fromConfig(LeadProcessStageConfig.from(StageConfiguration.enabled("BASIC_DETAILS", 1, true, false)), true);

        assertThatThrownBy(() -> stage.skip("not needed")).isInstanceOf(LeadStageRuleViolationException.class);
    }

    @Test
    void publishedProcessDefinitionCannotBeMutated() {
        final LeadProcessDefinitionVersion definitionVersion = LeadProcessDefinitionVersion.draft(1L, 1);
        definitionVersion.setId(20L);
        definitionVersion.publish();

        assertThatThrownBy(() -> definitionVersion
                .addStageConfiguration(LeadProcessStageConfig.from(StageConfiguration.enabled("NEXT", 2, false, true))))
                .isInstanceOf(LeadProcessDefinitionImmutableException.class);
    }

    @Test
    void staleStageVersionIsRejected() {
        final LeadStageInstance stage = LeadStageInstance
                .fromConfig(LeadProcessStageConfig.from(StageConfiguration.enabled("BASIC_DETAILS", 1, true, false)), true);
        stage.setId(30L);
        stage.setVersion(4L);

        assertThatThrownBy(() -> stage.assertVersion(3L)).isInstanceOf(LeadStageConcurrencyException.class);
    }

    @Test
    void noRealExternalRequestOccursInTests() {
        final FakeCreditBureauPrefillProvider provider = new FakeCreditBureauPrefillProvider();

        provider.requestPrefill(new org.apache.fineract.lead.domain.stage.port.ExternalStagePrefillRequest(
                CreditBureauStageHandler.STAGE_CODE, creditBureauContext("prefill-key-3", false), "prefill-key-3"));

        assertThat(provider.realExternalRequestCount()).isZero();
    }

    private BasicDetailsStageHandler basicDetailsHandler() {
        return new BasicDetailsStageHandler(List.of(), new InMemoryDatatablePort(), new NoopDocumentPort(), makerCheckerPolicy);
    }

    private LeadStageContext creditBureauContext(String idempotencyKey, boolean simulateFailure) {
        return new LeadStageContext(1L, 2L, 99L, CreditBureauStageHandler.STAGE_CODE, 3L, 4L, 5L,
                new StageConfiguration(CreditBureauStageHandler.STAGE_CODE, true, 2, false, true, false,
                        Set.of(CreditBureauStageHandler.DEFAULT_DATATABLE_NAME), Set.of(), true, true, Map.of()),
                Map.of(CreditBureauStageHandler.IDEMPOTENCY_KEY_ATTRIBUTE, idempotencyKey,
                        FakeCreditBureauPrefillProvider.SIMULATE_FAILURE_ATTRIBUTE, simulateFailure));
    }

    private static LeadStageHandler dummy(String stageCode) {
        return new AbstractLeadStageHandler(List.of(), new InMemoryDatatablePort(), new NoopDocumentPort(),
                new DefaultLeadStageMakerCheckerPolicy()) {

            @Override
            public String stageCode() {
                return stageCode;
            }

            @Override
            public Set<StageCapability> capabilities() {
                return Set.of();
            }

            @Override
            public LeadStageExecutionResult executeAction(LeadStageAction action, LeadStageContext context) {
                return LeadStageExecutionResult.accepted(LeadStageStatus.IN_PROGRESS, "ok");
            }
        };
    }

    private static final class InMemoryDatatablePort implements FineractDatatablePort {

        private final Map<Long, Map<String, Map<String, Object>>> values = new HashMap<>();
        private int writes;

        @Override
        public void validateRegistration(String stageCode, Set<String> datatableNames) {}

        @Override
        public LeadStageValidationResult validateRequiredStageData(Long stageInstanceId, Set<String> datatableNames) {
            return LeadStageValidationResult.valid();
        }

        @Override
        public Map<String, Object> readStageData(Long stageInstanceId, String datatableName) {
            return values.getOrDefault(stageInstanceId, Map.of()).getOrDefault(datatableName, Map.of());
        }

        @Override
        public void upsertStageData(Long stageInstanceId, String datatableName, Map<String, Object> newValues) {
            values.computeIfAbsent(stageInstanceId, ignored -> new HashMap<>()).put(datatableName, Map.copyOf(newValues));
            writes++;
        }

        int writeCount() {
            return writes;
        }
    }

    private static final class NoopDocumentPort implements FineractDocumentPort {

        @Override
        public LeadStageDocument upload(Long leadId, Long stageInstanceId, StageDocumentUpload upload) {
            return null;
        }

        @Override
        public List<LeadStageDocument> findByStage(Long stageInstanceId) {
            return new ArrayList<>();
        }

        @Override
        public void verify(Long documentId, Long checkerUserId, String remarks) {}

        @Override
        public void reject(Long documentId, Long checkerUserId, String reason) {}

        @Override
        public LeadStageValidationResult validateRequirements(Long stageInstanceId, List<LeadStageDocumentRequirement> requirements) {
            return LeadStageValidationResult.valid();
        }
    }
}
