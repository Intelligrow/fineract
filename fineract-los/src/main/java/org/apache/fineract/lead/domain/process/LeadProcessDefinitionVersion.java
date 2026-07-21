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
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.lead.exception.LeadProcessDefinitionImmutableException;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_lead_process_definition_version")
public class LeadProcessDefinitionVersion extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "process_definition_id", nullable = false)
    private Long processDefinitionId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_on_utc")
    private OffsetDateTime publishedOn;

    @Version
    @Column(name = "lock_version")
    private Long lockVersion;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_version_id")
    @OrderBy("sequence ASC")
    private List<LeadProcessStageConfig> stageConfigurations = new ArrayList<>();

    public static LeadProcessDefinitionVersion draft(Long processDefinitionId, Integer versionNumber) {
        final LeadProcessDefinitionVersion version = new LeadProcessDefinitionVersion();
        version.processDefinitionId = processDefinitionId;
        version.versionNumber = versionNumber;
        version.published = false;
        return version;
    }

    public void addStageConfiguration(LeadProcessStageConfig stageConfiguration) {
        assertMutable();
        this.stageConfigurations.add(stageConfiguration);
        this.stageConfigurations.sort(Comparator.comparingInt(LeadProcessStageConfig::getSequence));
    }

    public void replaceStageConfigurations(Collection<LeadProcessStageConfig> stageConfigurations) {
        assertMutable();
        this.stageConfigurations.clear();
        if (stageConfigurations != null) {
            this.stageConfigurations.addAll(stageConfigurations);
        }
        this.stageConfigurations.sort(Comparator.comparingInt(LeadProcessStageConfig::getSequence));
    }

    public void publish() {
        assertMutable();
        this.published = true;
        this.publishedOn = OffsetDateTime.now();
    }

    public void assertMutable() {
        if (published) {
            throw new LeadProcessDefinitionImmutableException(getId());
        }
    }
}
