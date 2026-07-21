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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.lead.data.LeadStagePrefillResponse;
import org.apache.fineract.lead.domain.lead.Lead;
import org.apache.fineract.lead.domain.stage.LeadStageContext;
import org.apache.fineract.lead.domain.stage.LeadStageHandler;
import org.apache.fineract.lead.domain.stage.LeadStageInstance;
import org.apache.fineract.lead.domain.stage.LeadStagePrefillResult;
import org.apache.fineract.lead.domain.stage.LeadStageRegistry;
import org.apache.fineract.lead.domain.stage.StageConfiguration;
import org.apache.fineract.lead.infrastructure.persistence.LeadRepository;
import org.apache.fineract.lead.infrastructure.persistence.LeadStageInstanceRepository;
import org.apache.fineract.lead.stages.creditbureau.CreditBureauStageHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class LeadStageApplicationService {

    private final LeadRepository leadRepository;
    private final LeadStageInstanceRepository stageInstanceRepository;
    private final LeadStageRegistry stageRegistry;

    public LeadStagePrefillResponse prefill(Long leadId, Long stageInstanceId, String idempotencyKey, Map<String, Object> attributes,
            Long actingUserId) {
        final Lead lead = leadRepository.findById(leadId).orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));
        final LeadStageInstance stage = stageInstanceRepository.findById(stageInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Lead stage not found: " + stageInstanceId));
        final LeadStageHandler handler = stageRegistry.getRequired(stage.getStageCode());
        final LeadStagePrefillResult result = handler.prefill(context(lead, stage, idempotencyKey, attributes, actingUserId));
        if (result.isSuccessful()) {
            stage.markPrefilled();
            stageInstanceRepository.save(stage);
        }
        return LeadStagePrefillResponse.from(result);
    }

    LeadStageContext context(Lead lead, LeadStageInstance stage, String idempotencyKey, Map<String, Object> attributes, Long actingUserId) {
        final Map<String, Object> contextAttributes = new LinkedHashMap<>();
        if (attributes != null) {
            attributes.entrySet().stream().filter(entry -> entry.getValue() != null)
                    .forEach(entry -> contextAttributes.put(entry.getKey(), entry.getValue()));
        }
        if (StringUtils.isNotBlank(idempotencyKey)) {
            contextAttributes.put(CreditBureauStageHandler.IDEMPOTENCY_KEY_ATTRIBUTE, idempotencyKey);
        }
        return new LeadStageContext(lead.getId(), stage.getProcessInstanceId(), stage.getId(), stage.getStageCode(), lead.getOfficeId(),
                lead.getAssignedStaffId(), actingUserId, stageConfiguration(stage), contextAttributes);
    }

    private static StageConfiguration stageConfiguration(LeadStageInstance stage) {
        final Set<String> datatables = CreditBureauStageHandler.STAGE_CODE.equals(stage.getStageCode())
                ? Set.of(CreditBureauStageHandler.DEFAULT_DATATABLE_NAME)
                : Set.of();
        return new StageConfiguration(stage.getStageCode(), true, stage.getSequence(), stage.isMandatory(), stage.isSkippable(), false,
                datatables, Set.of(), CreditBureauStageHandler.STAGE_CODE.equals(stage.getStageCode()), true, Map.of());
    }
}
