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
package org.apache.fineract.lead.domain.stage;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.fineract.lead.domain.document.LeadStageDocumentRequirement;
import org.apache.fineract.lead.domain.policy.LeadStageMakerCheckerPolicy;
import org.apache.fineract.lead.domain.policy.LeadStageValidationPolicy;
import org.apache.fineract.lead.domain.stage.port.FineractDatatablePort;
import org.apache.fineract.lead.domain.stage.port.FineractDocumentPort;

public abstract class AbstractLeadStageHandler implements LeadStageHandler {

    private final List<LeadStageValidationPolicy> validationPolicies;
    private final FineractDatatablePort datatablePort;
    private final FineractDocumentPort documentPort;
    @SuppressWarnings("unused")
    private final LeadStageMakerCheckerPolicy makerCheckerPolicy;

    protected AbstractLeadStageHandler(Collection<LeadStageValidationPolicy> validationPolicies, FineractDatatablePort datatablePort,
            FineractDocumentPort documentPort, LeadStageMakerCheckerPolicy makerCheckerPolicy) {
        this.validationPolicies = validationPolicies == null ? List.of() : List.copyOf(validationPolicies);
        this.datatablePort = datatablePort;
        this.documentPort = documentPort;
        this.makerCheckerPolicy = makerCheckerPolicy;
    }

    @Override
    public LeadStageValidationResult validateEntry(LeadStageContext context) {
        return validatePolicies(context);
    }

    @Override
    public LeadStageValidationResult validateSubmission(LeadStageContext context) {
        return LeadStageValidationResult.merge(validatePolicies(context),
                datatablePort.validateRequiredStageData(context.stageInstanceId(), supportedDatatableNames(context.configuration())),
                documentPort.validateRequirements(context.stageInstanceId(), documentRequirements(context)));
    }

    @Override
    public LeadStageValidationResult validateCompletion(LeadStageContext context) {
        return validateSubmission(context);
    }

    @Override
    public Set<String> supportedDatatableNames(StageConfiguration configuration) {
        return configuration == null ? Set.of() : configuration.requiredDatatables();
    }

    @Override
    public List<LeadStageDocumentRequirement> documentRequirements(LeadStageContext context) {
        if (context == null || context.configuration() == null) {
            return List.of();
        }
        return List.copyOf(context.configuration().documentRequirements());
    }

    protected LeadStageValidationResult validatePolicies(LeadStageContext context) {
        return LeadStageValidationResult.merge(validationPolicies.stream().map(policy -> policy.validate(context)).toList());
    }
}
