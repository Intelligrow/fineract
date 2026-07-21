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
import org.apache.fineract.lead.data.AvailableLeadStageData;
import org.apache.fineract.lead.data.LeadProcessDefinitionData;
import org.apache.fineract.lead.data.LeadProcessDefinitionVersionData;
import org.apache.fineract.lead.domain.process.LeadProcessDefinition;
import org.apache.fineract.lead.domain.process.LeadProcessDefinitionVersion;
import org.apache.fineract.lead.domain.stage.LeadStageRegistry;
import org.apache.fineract.lead.infrastructure.persistence.LeadProcessDefinitionRepository;
import org.apache.fineract.lead.infrastructure.persistence.LeadProcessDefinitionVersionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class LeadProcessReadPlatformServiceImpl implements LeadProcessReadPlatformService {

    private final LeadStageRegistry stageRegistry;
    private final LeadProcessDefinitionRepository processDefinitionRepository;
    private final LeadProcessDefinitionVersionRepository processDefinitionVersionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AvailableLeadStageData> retrieveAvailableStages() {
        return stageRegistry.stageCodes().stream().sorted()
                .map(stageCode -> new AvailableLeadStageData(stageCode, stageRegistry.getRequired(stageCode).capabilities())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadProcessDefinitionData> retrieveAllProcessDefinitions() {
        return processDefinitionRepository.findAll().stream().sorted(Comparator.comparing(LeadProcessDefinition::getName))
                .map(LeadProcessDefinitionData::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LeadProcessDefinitionData retrieveProcessDefinition(Long processDefinitionId) {
        return LeadProcessDefinitionData.from(findProcessDefinition(processDefinitionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeadProcessDefinitionVersionData> retrieveProcessDefinitionVersions(Long processDefinitionId) {
        findProcessDefinition(processDefinitionId);
        return processDefinitionVersionRepository.findByProcessDefinitionIdOrderByVersionNumberDesc(processDefinitionId).stream()
                .map(LeadProcessDefinitionVersionData::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LeadProcessDefinitionVersionData retrieveProcessDefinitionVersion(Long processDefinitionId, Long versionId) {
        return LeadProcessDefinitionVersionData.from(findVersion(processDefinitionId, versionId));
    }

    private LeadProcessDefinition findProcessDefinition(Long processDefinitionId) {
        return processDefinitionRepository.findById(processDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("Lead process definition not found: " + processDefinitionId));
    }

    private LeadProcessDefinitionVersion findVersion(Long processDefinitionId, Long versionId) {
        final LeadProcessDefinitionVersion version = processDefinitionVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Lead process definition version not found: " + versionId));
        if (!processDefinitionId.equals(version.getProcessDefinitionId())) {
            throw new IllegalArgumentException(
                    "Lead process definition version does not belong to process definition: " + processDefinitionId);
        }
        return version;
    }
}
