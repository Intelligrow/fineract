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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_agent_collection_transaction", uniqueConstraints = {
        @UniqueConstraint(name = "uk_m_agent_collection_loan_transaction", columnNames = { "loan_transaction_id" }),
        @UniqueConstraint(name = "uk_m_agent_collection_savings_transaction", columnNames = { "savings_transaction_id" }) })
public class AgentCollectionTransaction extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "transaction_type_enum", nullable = false)
    private Integer transactionType;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "loan_transaction_id")
    private Long loanTransactionId;

    @Column(name = "savings_account_id")
    private Long savingsAccountId;

    @Column(name = "savings_transaction_id")
    private Long savingsTransactionId;

    @Column(name = "payment_type_id", nullable = false)
    private Long paymentTypeId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "status_enum", nullable = false)
    private Integer status;

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "settled_date")
    private LocalDate settledDate;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "mobile_reference", length = 100)
    private String mobileReference;

    @Version
    @Column(name = "version")
    private Long version;

    protected AgentCollectionTransaction() {}

    private AgentCollectionTransaction(final Agent agent, final Long officeId, final AgentTransactionType transactionType,
            final Long loanId, final Long loanTransactionId, final Long savingsAccountId, final Long savingsTransactionId,
            final Long paymentTypeId, final BigDecimal amount, final String currencyCode, final LocalDate transactionDate,
            final String externalId, final String mobileReference) {
        this.agent = agent;
        this.officeId = officeId;
        this.transactionType = transactionType.getValue();
        this.loanId = loanId;
        this.loanTransactionId = loanTransactionId;
        this.savingsAccountId = savingsAccountId;
        this.savingsTransactionId = savingsTransactionId;
        this.paymentTypeId = paymentTypeId;
        this.amount = amount;
        this.currencyCode = StringUtils.upperCase(currencyCode);
        this.transactionDate = transactionDate;
        this.status = AgentCollectionTransactionStatusType.PENDING.getValue();
        this.externalId = externalId;
        this.mobileReference = mobileReference;
    }

    public static AgentCollectionTransaction loanRepayment(final Agent agent, final Long officeId, final Long loanId,
            final Long loanTransactionId, final Long paymentTypeId, final BigDecimal amount, final String currencyCode,
            final LocalDate transactionDate, final String externalId, final String mobileReference) {
        return new AgentCollectionTransaction(agent, officeId, AgentTransactionType.LOAN_REPAYMENT, loanId, loanTransactionId, null, null,
                paymentTypeId, amount, currencyCode, transactionDate, externalId, mobileReference);
    }

    public static AgentCollectionTransaction savingsDeposit(final Agent agent, final Long officeId, final Long savingsAccountId,
            final Long savingsTransactionId, final Long paymentTypeId, final BigDecimal amount, final String currencyCode,
            final LocalDate transactionDate, final String externalId, final String mobileReference) {
        return new AgentCollectionTransaction(agent, officeId, AgentTransactionType.SAVINGS_DEPOSIT, null, null, savingsAccountId,
                savingsTransactionId, paymentTypeId, amount, currencyCode, transactionDate, externalId, mobileReference);
    }

    public Long getAgentId() {
        return this.agent.getId();
    }

    public Long getOfficeId() {
        return officeId;
    }

    public Integer getTransactionType() {
        return transactionType;
    }

    public Long getLoanId() {
        return loanId;
    }

    public Long getLoanTransactionId() {
        return loanTransactionId;
    }

    public Long getSavingsAccountId() {
        return savingsAccountId;
    }

    public Long getSavingsTransactionId() {
        return savingsTransactionId;
    }

    public Long getPaymentTypeId() {
        return paymentTypeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public Integer getStatus() {
        return status;
    }

    public Long getSettlementId() {
        return settlementId;
    }

    public LocalDate getSettledDate() {
        return settledDate;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getMobileReference() {
        return mobileReference;
    }

    public boolean isPending() {
        return Objects.equals(this.status, AgentCollectionTransactionStatusType.PENDING.getValue());
    }

    public boolean isSettled() {
        return Objects.equals(this.status, AgentCollectionTransactionStatusType.SETTLED.getValue());
    }

    public boolean isReversed() {
        return Objects.equals(this.status, AgentCollectionTransactionStatusType.REVERSED.getValue());
    }

    public void markAsReversed() {
        this.status = AgentCollectionTransactionStatusType.REVERSED.getValue();
    }
}
