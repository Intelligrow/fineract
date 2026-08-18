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
package org.apache.fineract.organisation.agentcollection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_agent_settlement")
public class AgentSettlement extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal expectedAmount;

    @Column(name = "submitted_amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal submittedAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "status_enum", nullable = false)
    private Integer status;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "submitted_by_user_id")
    private Long submittedByUserId;

    @Column(name = "submitted_on_date")
    private LocalDate submittedOnDate;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_on_date")
    private LocalDate approvedOnDate;

    @Column(name = "rejected_by_user_id")
    private Long rejectedByUserId;

    @Column(name = "rejected_on_date")
    private LocalDate rejectedOnDate;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

    @Column(name = "cancelled_on_date")
    private LocalDate cancelledOnDate;

    @Version
    @Column(name = "version")
    private Long version;

    protected AgentSettlement() {}

    private AgentSettlement(final Agent agent, final LocalDate settlementDate, final BigDecimal expectedAmount,
            final BigDecimal submittedAmount, final String referenceNumber, final String notes) {
        this.agent = agent;
        this.officeId = agent.getOffice().getId();
        this.settlementDate = settlementDate;
        this.expectedAmount = expectedAmount;
        this.submittedAmount = submittedAmount;
        this.currencyCode = StringUtils.upperCase(agent.getCurrencyCode());
        this.referenceNumber = referenceNumber;
        this.notes = notes;
        this.status = AgentSettlementStatusType.DRAFT.getValue();
    }

    public static AgentSettlement draft(final Agent agent, final LocalDate settlementDate, final BigDecimal expectedAmount,
            final BigDecimal submittedAmount, final String referenceNumber, final String notes) {
        return new AgentSettlement(agent, settlementDate, expectedAmount, submittedAmount, referenceNumber, notes);
    }

    public Agent getAgent() {
        return agent;
    }

    public Long getAgentId() {
        return this.agent.getId();
    }

    public Long getOfficeId() {
        return officeId;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public BigDecimal getSubmittedAmount() {
        return submittedAmount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Integer getStatus() {
        return status;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getNotes() {
        return notes;
    }

    public Long getSubmittedByUserId() {
        return submittedByUserId;
    }

    public LocalDate getSubmittedOnDate() {
        return submittedOnDate;
    }

    public Long getApprovedByUserId() {
        return approvedByUserId;
    }

    public LocalDate getApprovedOnDate() {
        return approvedOnDate;
    }

    public Long getRejectedByUserId() {
        return rejectedByUserId;
    }

    public LocalDate getRejectedOnDate() {
        return rejectedOnDate;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getCancelledByUserId() {
        return cancelledByUserId;
    }

    public LocalDate getCancelledOnDate() {
        return cancelledOnDate;
    }

    public AgentSettlementStatusType status() {
        return AgentSettlementStatusType.fromInt(this.status);
    }

    public boolean isDraft() {
        return Objects.equals(this.status, AgentSettlementStatusType.DRAFT.getValue());
    }

    public boolean isSubmitted() {
        return Objects.equals(this.status, AgentSettlementStatusType.SUBMITTED.getValue());
    }

    public void submit(final Long userId, final LocalDate submittedOnDate) {
        this.submittedByUserId = userId;
        this.submittedOnDate = submittedOnDate;
        this.status = AgentSettlementStatusType.SUBMITTED.getValue();
    }

    public void approve(final Long userId, final LocalDate approvedOnDate) {
        this.approvedByUserId = userId;
        this.approvedOnDate = approvedOnDate;
        this.status = AgentSettlementStatusType.APPROVED.getValue();
    }

    public void reject(final Long userId, final LocalDate rejectedOnDate, final String rejectionReason) {
        this.rejectedByUserId = userId;
        this.rejectedOnDate = rejectedOnDate;
        this.rejectionReason = rejectionReason;
        this.status = AgentSettlementStatusType.REJECTED.getValue();
    }

    public void cancel(final Long userId, final LocalDate cancelledOnDate) {
        this.cancelledByUserId = userId;
        this.cancelledOnDate = cancelledOnDate;
        this.status = AgentSettlementStatusType.CANCELLED.getValue();
    }
}
