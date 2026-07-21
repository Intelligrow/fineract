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

import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.lead.data.LeadData;
import org.apache.fineract.lead.data.LeadStageData;
import org.apache.fineract.lead.domain.lead.Lead;
import org.apache.fineract.lead.domain.process.LeadProcessInstance;
import org.apache.fineract.lead.infrastructure.persistence.LeadProcessInstanceRepository;
import org.apache.fineract.lead.infrastructure.persistence.LeadRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class LeadReadPlatformServiceImpl implements LeadReadPlatformService {

    private final LeadRepository leadRepository;
    private final LeadProcessInstanceRepository processInstanceRepository;

    @Override
    @Transactional(readOnly = true)
    public LeadData retrieveOne(Long leadId) {
        return LeadData.from(findLead(leadId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadStageData> retrieveStages(Long leadId) {
        findLead(leadId);
        return processInstanceRepository.findByLeadId(leadId).stream().max(Comparator.comparing(LeadProcessInstance::getId))
                .map(process -> process.getStageInstances().stream().sorted(Comparator.comparingInt(stage -> stage.getSequence()))
                        .map(LeadStageData::from).toList())
                .orElseGet(List::of);
    }

    private Lead findLead(Long leadId) {
        return leadRepository.findById(leadId).orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));
    }
}
