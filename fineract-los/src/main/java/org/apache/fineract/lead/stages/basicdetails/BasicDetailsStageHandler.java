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
package org.apache.fineract.lead.stages.basicdetails;

import java.util.Collection;
import java.util.Set;
import org.apache.fineract.lead.domain.policy.LeadStageMakerCheckerPolicy;
import org.apache.fineract.lead.domain.policy.LeadStageValidationPolicy;
import org.apache.fineract.lead.domain.stage.AbstractLeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageAction;
import org.apache.fineract.lead.domain.stage.LeadStageContext;
import org.apache.fineract.lead.domain.stage.LeadStageExecutionResult;
import org.apache.fineract.lead.domain.stage.LeadStageStatus;
import org.apache.fineract.lead.domain.stage.StageCapability;
import org.apache.fineract.lead.domain.stage.port.FineractDatatablePort;
import org.apache.fineract.lead.domain.stage.port.FineractDocumentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class BasicDetailsStageHandler extends AbstractLeadStageHandler {

    public static final String STAGE_CODE = "BASIC_DETAILS";

    public BasicDetailsStageHandler(Collection<LeadStageValidationPolicy> validationPolicies, FineractDatatablePort datatablePort,
            FineractDocumentPort documentPort, LeadStageMakerCheckerPolicy makerCheckerPolicy) {
        super(validationPolicies, datatablePort, documentPort, makerCheckerPolicy);
    }

    @Override
    public String stageCode() {
        return STAGE_CODE;
    }

    @Override
    public Set<StageCapability> capabilities() {
        return Set.of(StageCapability.MANUAL_DATA, StageCapability.DATATABLES, StageCapability.DOCUMENTS, StageCapability.MAKER_CHECKER);
    }

    @Override
    public LeadStageExecutionResult executeAction(LeadStageAction action, LeadStageContext context) {
        return switch (action) {
            case START, SAVE -> LeadStageExecutionResult.accepted(LeadStageStatus.IN_PROGRESS, "Basic details stage updated.");
            case SUBMIT_FOR_CHECK ->
                LeadStageExecutionResult.accepted(LeadStageStatus.PENDING_CHECK, "Basic details submitted for checker approval.");
            case CHECK_APPROVE, COMPLETE -> LeadStageExecutionResult.accepted(LeadStageStatus.COMPLETED, "Basic details stage completed.");
            case CHECK_RETURN -> LeadStageExecutionResult.accepted(LeadStageStatus.RETURNED, "Basic details returned for rework.");
            default -> LeadStageExecutionResult.rejected("Unsupported action for BASIC_DETAILS.");
        };
    }
}
