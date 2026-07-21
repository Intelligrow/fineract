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
package org.apache.fineract.lead.stages.creditbureau;

import java.util.Collection;
import java.util.Set;
import org.apache.fineract.lead.domain.policy.LeadStageMakerCheckerPolicy;
import org.apache.fineract.lead.domain.policy.LeadStageValidationPolicy;
import org.apache.fineract.lead.domain.stage.AbstractLeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageAction;
import org.apache.fineract.lead.domain.stage.LeadStageContext;
import org.apache.fineract.lead.domain.stage.LeadStageExecutionResult;
import org.apache.fineract.lead.domain.stage.LeadStagePrefillResult;
import org.apache.fineract.lead.domain.stage.LeadStageStatus;
import org.apache.fineract.lead.domain.stage.StageCapability;
import org.apache.fineract.lead.domain.stage.port.ExternalStagePrefillPort;
import org.apache.fineract.lead.domain.stage.port.ExternalStagePrefillRequest;
import org.apache.fineract.lead.domain.stage.port.ExternalStagePrefillResponse;
import org.apache.fineract.lead.domain.stage.port.FineractDatatablePort;
import org.apache.fineract.lead.domain.stage.port.FineractDocumentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class CreditBureauStageHandler extends AbstractLeadStageHandler {

    public static final String STAGE_CODE = "CREDIT_BUREAU";
    public static final String IDEMPOTENCY_KEY_ATTRIBUTE = "idempotencyKey";
    public static final String DEFAULT_DATATABLE_NAME = "lead_credit_bureau_prefill";

    private final ExternalStagePrefillPort externalStagePrefillPort;
    private final FineractDatatablePort datatablePort;

    public CreditBureauStageHandler(Collection<LeadStageValidationPolicy> validationPolicies, FineractDatatablePort datatablePort,
            FineractDocumentPort documentPort, LeadStageMakerCheckerPolicy makerCheckerPolicy,
            ExternalStagePrefillPort externalStagePrefillPort) {
        super(validationPolicies, datatablePort, documentPort, makerCheckerPolicy);
        this.externalStagePrefillPort = externalStagePrefillPort;
        this.datatablePort = datatablePort;
    }

    @Override
    public String stageCode() {
        return STAGE_CODE;
    }

    @Override
    public Set<StageCapability> capabilities() {
        return Set.of(StageCapability.EXTERNAL_PREFILL, StageCapability.DATATABLES, StageCapability.MANUAL_FALLBACK);
    }

    @Override
    public LeadStagePrefillResult prefill(LeadStageContext context) {
        final String idempotencyKey;
        try {
            idempotencyKey = context.requiredAttributeAsString(IDEMPOTENCY_KEY_ATTRIBUTE);
        } catch (IllegalArgumentException e) {
            return LeadStagePrefillResult.failed("Credit bureau prefill requires an idempotency key.");
        }

        final ExternalStagePrefillResponse response = externalStagePrefillPort
                .requestPrefill(new ExternalStagePrefillRequest(STAGE_CODE, context, idempotencyKey));
        if (!response.successful()) {
            return LeadStagePrefillResult.failed(response.message());
        }
        if (response.idempotentReplay()) {
            try {
                if (datatablePort.readStageData(context.stageInstanceId(), datatableName(context)).isEmpty()) {
                    return LeadStagePrefillResult.failed("Credit bureau prefill replay has no lead datatable state.");
                }
            } catch (UnsupportedOperationException e) {
                return LeadStagePrefillResult.failed("Credit bureau prefill replay cannot verify unresolved lead datatable state.");
            }
            return LeadStagePrefillResult.idempotentReplay(response.providerReference(), response.normalizedValues());
        }

        try {
            datatablePort.upsertStageData(context.stageInstanceId(), datatableName(context), response.normalizedValues());
        } catch (UnsupportedOperationException e) {
            return LeadStagePrefillResult.failed("Credit bureau prefill succeeded, but lead datatable integration is unresolved.");
        }
        return LeadStagePrefillResult.completed(response.providerReference(), response.normalizedValues());
    }

    @Override
    public LeadStageExecutionResult executeAction(LeadStageAction action, LeadStageContext context) {
        return switch (action) {
            case PREFILL, RETRY_PREFILL ->
                LeadStageExecutionResult.accepted(LeadStageStatus.PENDING_EXTERNAL, "Credit bureau prefill requested.");
            case SAVE -> LeadStageExecutionResult.accepted(LeadStageStatus.IN_PROGRESS, "Credit bureau data saved manually.");
            case COMPLETE -> LeadStageExecutionResult.accepted(LeadStageStatus.COMPLETED, "Credit bureau stage completed.");
            default -> LeadStageExecutionResult.rejected("Unsupported action for CREDIT_BUREAU.");
        };
    }

    private static String datatableName(LeadStageContext context) {
        final Set<String> configuredDatatables = context.configuration() == null ? Set.of() : context.configuration().requiredDatatables();
        if (configuredDatatables.isEmpty()) {
            return DEFAULT_DATATABLE_NAME;
        }
        return configuredDatatables.iterator().next();
    }
}
