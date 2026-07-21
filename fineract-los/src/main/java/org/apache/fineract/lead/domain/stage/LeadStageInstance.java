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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.lead.domain.process.LeadProcessStageConfig;
import org.apache.fineract.lead.exception.LeadStageConcurrencyException;
import org.apache.fineract.lead.exception.LeadStageRuleViolationException;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_lead_stage_instance")
public class LeadStageInstance extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "process_instance_id", insertable = false, updatable = false)
    private Long processInstanceId;

    @Column(name = "stage_code", nullable = false, length = 100)
    private String stageCode;

    @Column(name = "sequence_no", nullable = false)
    private int sequence;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "skippable", nullable = false)
    private boolean skippable;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeadStageStatus status;

    @Column(name = "submission_revision", nullable = false)
    private long revision;

    @Column(name = "pending_check_revision")
    private Long pendingCheckRevision;

    @Version
    @Column(name = "version")
    private Long version;

    public static LeadStageInstance fromConfig(LeadProcessStageConfig config, boolean ready) {
        final LeadStageInstance stage = new LeadStageInstance();
        stage.stageCode = config.getStageCode();
        stage.sequence = config.getSequence();
        stage.mandatory = config.isMandatory();
        stage.skippable = config.isSkippable();
        stage.status = ready ? LeadStageStatus.READY : LeadStageStatus.NOT_STARTED;
        stage.revision = 0;
        return stage;
    }

    public void skip(String reason) {
        if (mandatory) {
            throw new LeadStageRuleViolationException("Mandatory lead stages cannot be skipped.");
        }
        if (!skippable) {
            throw new LeadStageRuleViolationException("Lead stage is not configured as skippable.");
        }
        if (StringUtils.isBlank(reason)) {
            throw new LeadStageRuleViolationException("Skipping a lead stage requires a reason.");
        }
        this.status = LeadStageStatus.SKIPPED;
    }

    public void assertVersion(Long expectedVersion) {
        if (!Objects.equals(this.version, expectedVersion)) {
            throw new LeadStageConcurrencyException(getId(), expectedVersion, this.version);
        }
    }

    public void recordDataChange() {
        this.revision++;
        if (this.status == LeadStageStatus.PENDING_CHECK) {
            this.pendingCheckRevision = null;
            this.status = LeadStageStatus.IN_PROGRESS;
        }
    }

    public void submitForCheck() {
        this.pendingCheckRevision = revision;
        this.status = LeadStageStatus.PENDING_CHECK;
    }

    public void markPrefilled() {
        this.status = LeadStageStatus.PREFILLED;
    }

    public void complete() {
        this.pendingCheckRevision = null;
        this.status = LeadStageStatus.COMPLETED;
    }

    public void returnForRework() {
        this.pendingCheckRevision = null;
        this.status = LeadStageStatus.RETURNED;
    }
}
