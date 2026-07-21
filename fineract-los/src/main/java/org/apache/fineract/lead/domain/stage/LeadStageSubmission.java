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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_lead_stage_submission")
public class LeadStageSubmission extends AbstractPersistableCustom<Long> {

    @Column(name = "stage_instance_id", nullable = false)
    private Long stageInstanceId;

    @Column(name = "maker_user_id", nullable = false)
    private Long makerUserId;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeadStageSubmissionStatus status;

    @Column(name = "submitted_on_utc", nullable = false)
    private OffsetDateTime submittedOn;

    public static LeadStageSubmission pending(Long stageInstanceId, Long makerUserId, long revision) {
        final LeadStageSubmission submission = new LeadStageSubmission();
        submission.stageInstanceId = stageInstanceId;
        submission.makerUserId = makerUserId;
        submission.revision = revision;
        submission.status = LeadStageSubmissionStatus.PENDING;
        submission.submittedOn = OffsetDateTime.now();
        return submission;
    }

    public void approve() {
        this.status = LeadStageSubmissionStatus.APPROVED;
    }

    public void returnForRework() {
        this.status = LeadStageSubmissionStatus.RETURNED;
    }

    public void supersede() {
        this.status = LeadStageSubmissionStatus.SUPERSEDED;
    }
}
