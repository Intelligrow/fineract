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

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "m_agent", uniqueConstraints = { @UniqueConstraint(name = "uk_m_agent_appuser", columnNames = { "appuser_id" }),
        @UniqueConstraint(name = "uk_m_agent_staff", columnNames = { "staff_id" }) })
public class Agent extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appuser_id", nullable = false)
    private AppUser appUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_type_id", nullable = false)
    private PaymentType paymentType;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "status_enum", nullable = false)
    private Integer status;

    @Column(name = "maximum_cash_in_hand", nullable = false, precision = 19, scale = 6)
    private BigDecimal maximumCashInHand;

    @Column(name = "maximum_daily_total_collection", nullable = false, precision = 19, scale = 6)
    private BigDecimal maximumDailyTotalCollection;

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("transactionType ASC")
    private Set<AgentTransactionLimit> transactionLimits = new HashSet<>();

    @Version
    @Column(name = "version")
    private Long version;

    protected Agent() {}

    private Agent(final AppUser appUser, final Staff staff, final Office office, final PaymentType paymentType, final String currencyCode,
            final BigDecimal maximumCashInHand, final BigDecimal maximumDailyTotalCollection, final boolean active) {
        this.appUser = appUser;
        this.staff = staff;
        this.office = office;
        this.paymentType = paymentType;
        this.currencyCode = StringUtils.upperCase(currencyCode);
        this.maximumCashInHand = maximumCashInHand;
        this.maximumDailyTotalCollection = maximumDailyTotalCollection;
        this.status = active ? AgentStatusType.ACTIVE.getValue() : AgentStatusType.INACTIVE.getValue();
    }

    public static Agent createNew(final AppUser appUser, final Staff staff, final Office office, final PaymentType paymentType,
            final String currencyCode, final BigDecimal maximumCashInHand, final BigDecimal maximumDailyTotalCollection,
            final boolean active) {
        return new Agent(appUser, staff, office, paymentType, currencyCode, maximumCashInHand, maximumDailyTotalCollection, active);
    }

    public AppUser getAppUser() {
        return appUser;
    }

    public Staff getStaff() {
        return staff;
    }

    public Office getOffice() {
        return office;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Integer getStatus() {
        return status;
    }

    public BigDecimal getMaximumCashInHand() {
        return maximumCashInHand;
    }

    public BigDecimal getMaximumDailyTotalCollection() {
        return maximumDailyTotalCollection;
    }

    public Set<AgentTransactionLimit> getTransactionLimits() {
        return transactionLimits;
    }

    public AgentStatusType status() {
        return AgentStatusType.fromInt(this.status);
    }

    public boolean activate() {
        if (status().isActive()) {
            return false;
        }
        this.status = AgentStatusType.ACTIVE.getValue();
        return true;
    }

    public boolean deactivate() {
        if (status().isInactive()) {
            return false;
        }
        this.status = AgentStatusType.INACTIVE.getValue();
        return true;
    }

    public void updateAppUser(final AppUser appUser) {
        this.appUser = appUser;
    }

    public void updateStaff(final Staff staff) {
        this.staff = staff;
    }

    public void updateOffice(final Office office) {
        this.office = office;
    }

    public void updatePaymentType(final PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public void updateCurrencyCode(final String currencyCode) {
        this.currencyCode = StringUtils.upperCase(currencyCode);
    }

    public void updateMaximumCashInHand(final BigDecimal maximumCashInHand) {
        this.maximumCashInHand = maximumCashInHand;
    }

    public void updateMaximumDailyTotalCollection(final BigDecimal maximumDailyTotalCollection) {
        this.maximumDailyTotalCollection = maximumDailyTotalCollection;
    }

    public AgentTransactionLimit addTransactionLimit(final AgentTransactionType transactionType, final BigDecimal maximumSingleAmount,
            final BigDecimal maximumDailyAmount, final boolean enabled) {
        final AgentTransactionLimit limit = AgentTransactionLimit.createNew(this, transactionType, maximumSingleAmount, maximumDailyAmount,
                enabled);
        this.transactionLimits.add(limit);
        return limit;
    }

    public Optional<AgentTransactionLimit> findTransactionLimit(final AgentTransactionType transactionType) {
        return this.transactionLimits.stream().filter(limit -> limit.hasTransactionType(transactionType)).findFirst();
    }
}
