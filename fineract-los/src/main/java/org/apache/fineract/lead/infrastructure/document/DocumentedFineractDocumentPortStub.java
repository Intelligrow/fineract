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
package org.apache.fineract.lead.infrastructure.document;

import java.util.List;
import org.apache.fineract.lead.domain.document.LeadStageDocument;
import org.apache.fineract.lead.domain.document.LeadStageDocumentRequirement;
import org.apache.fineract.lead.domain.document.StageDocumentUpload;
import org.apache.fineract.lead.domain.stage.LeadStageValidationResult;
import org.apache.fineract.lead.domain.stage.port.FineractDocumentPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class DocumentedFineractDocumentPortStub implements FineractDocumentPort {

    @Override
    public LeadStageDocument upload(Long leadId, Long stageInstanceId, StageDocumentUpload upload) {
        throw new UnsupportedOperationException("Lead stage document upload is behind an unresolved entity-type decision.");
    }

    @Override
    public List<LeadStageDocument> findByStage(Long stageInstanceId) {
        return List.of();
    }

    @Override
    public void verify(Long documentId, Long checkerUserId, String remarks) {
        throw new UnsupportedOperationException("Lead stage document verification must use lead-owned document link state.");
    }

    @Override
    public void reject(Long documentId, Long checkerUserId, String reason) {
        throw new UnsupportedOperationException("Lead stage document rejection must use lead-owned document link state.");
    }

    @Override
    public LeadStageValidationResult validateRequirements(Long stageInstanceId, List<LeadStageDocumentRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return LeadStageValidationResult.valid();
        }
        return LeadStageValidationResult.invalid("lead.document.integration.unresolved",
                "Lead stage document adapter is intentionally unresolved for this skeleton.", false);
    }
}
