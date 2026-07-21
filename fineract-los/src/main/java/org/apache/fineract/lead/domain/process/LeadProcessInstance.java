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
package org.apache.fineract.lead.domain.process;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.lead.domain.stage.LeadStageInstance;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_lead_process_instance")
public class LeadProcessInstance extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Column(name = "definition_version_id", nullable = false)
    private Long definitionVersionId;

    @Column(name = "definition_version_number", nullable = false)
    private Integer definitionVersionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeadProcessInstanceStatus status;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "process_instance_id")
    @org.hibernate.annotations.BatchSize(size = 25)
    private List<LeadStageInstance> stageInstances = new ArrayList<>();

    public static LeadProcessInstance start(Long leadId, LeadProcessDefinitionVersion definitionVersion) {
        final LeadProcessInstance instance = new LeadProcessInstance();
        instance.leadId = leadId;
        instance.definitionVersionId = definitionVersion.getId();
        instance.definitionVersionNumber = definitionVersion.getVersionNumber();
        instance.status = LeadProcessInstanceStatus.ACTIVE;

        final List<LeadProcessStageConfig> enabledStages = definitionVersion.getStageConfigurations().stream()
                .filter(LeadProcessStageConfig::isEnabled).sorted(Comparator.comparingInt(LeadProcessStageConfig::getSequence)).toList();
        for (int index = 0; index < enabledStages.size(); index++) {
            instance.stageInstances.add(LeadStageInstance.fromConfig(enabledStages.get(index), index == 0));
        }
        return instance;
    }
}
