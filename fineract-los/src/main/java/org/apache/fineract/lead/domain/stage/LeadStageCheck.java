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
@Table(name = "m_lead_stage_check")
public class LeadStageCheck extends AbstractPersistableCustom<Long> {

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "checker_user_id", nullable = false)
    private Long checkerUserId;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private LeadStageCheckDecision decision;

    @Column(name = "checked_on_utc", nullable = false)
    private OffsetDateTime checkedOn;

    @Column(name = "remarks", length = 500)
    private String remarks;

    public static LeadStageCheck record(Long submissionId, Long checkerUserId, long revision, LeadStageCheckDecision decision,
            String remarks) {
        final LeadStageCheck check = new LeadStageCheck();
        check.submissionId = submissionId;
        check.checkerUserId = checkerUserId;
        check.revision = revision;
        check.decision = decision;
        check.checkedOn = OffsetDateTime.now();
        check.remarks = remarks;
        return check;
    }
}
