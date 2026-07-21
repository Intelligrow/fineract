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
package org.apache.fineract.lead.infrastructure.datatable;

import java.util.Map;
import java.util.Set;
import org.apache.fineract.lead.domain.stage.LeadStageValidationResult;
import org.apache.fineract.lead.domain.stage.port.FineractDatatablePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class DocumentedFineractDatatablePortStub implements FineractDatatablePort {

    @Override
    public void validateRegistration(String stageCode, Set<String> datatableNames) {
        if (datatableNames != null && !datatableNames.isEmpty()) {
            throw new UnsupportedOperationException(
                    "Lead stage datatable registration requires m_lead_stage_instance support in EntityTables.");
        }
    }

    @Override
    public LeadStageValidationResult validateRequiredStageData(Long stageInstanceId, Set<String> datatableNames) {
        if (datatableNames == null || datatableNames.isEmpty()) {
            return LeadStageValidationResult.valid();
        }
        return LeadStageValidationResult.invalid("lead.datatable.integration.unresolved",
                "Lead stage datatable adapter is intentionally unresolved for this Fineract version.", false);
    }

    @Override
    public Map<String, Object> readStageData(Long stageInstanceId, String datatableName) {
        throw new UnsupportedOperationException("Lead stage datatable reads are behind an unresolved integration decision.");
    }

    @Override
    public void upsertStageData(Long stageInstanceId, String datatableName, Map<String, Object> values) {
        throw new UnsupportedOperationException("Lead stage datatable writes are behind an unresolved integration decision.");
    }
}
