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
package org.apache.fineract.lead.domain.lead;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_lead")
public class Lead extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeadStatus status;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "assigned_staff_id")
    private Long assignedStaffId;

    @Version
    @Column(name = "version")
    private Long version;

    public static Lead create(String externalId, Long officeId, Long assignedStaffId) {
        final Lead lead = new Lead();
        lead.externalId = externalId;
        lead.officeId = officeId;
        lead.assignedStaffId = assignedStaffId;
        lead.status = LeadStatus.DRAFT;
        return lead;
    }
}
