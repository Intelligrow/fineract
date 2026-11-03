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
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_agent_transaction_limit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_m_agent_transaction_limit_type", columnNames = { "agent_id", "transaction_type_enum" }) })
public class AgentTransactionLimit extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "transaction_type_enum", nullable = false)
    private Integer transactionType;

    @Column(name = "maximum_single_amount", precision = 19, scale = 6)
    private BigDecimal maximumSingleAmount;

    @Column(name = "maximum_daily_amount", precision = 19, scale = 6)
    private BigDecimal maximumDailyAmount;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Version
    @Column(name = "version")
    private Long version;

    protected AgentTransactionLimit() {}

    private AgentTransactionLimit(final Agent agent, final AgentTransactionType transactionType, final BigDecimal maximumSingleAmount,
            final BigDecimal maximumDailyAmount, final boolean enabled) {
        this.agent = agent;
        this.transactionType = transactionType.getValue();
        this.maximumSingleAmount = maximumSingleAmount;
        this.maximumDailyAmount = maximumDailyAmount;
        this.enabled = enabled;
    }

    public static AgentTransactionLimit createNew(final Agent agent, final AgentTransactionType transactionType,
            final BigDecimal maximumSingleAmount, final BigDecimal maximumDailyAmount, final boolean enabled) {
        return new AgentTransactionLimit(agent, transactionType, maximumSingleAmount, maximumDailyAmount, enabled);
    }

    public Long getAgentId() {
        return this.agent.getId();
    }

    public Integer getTransactionType() {
        return transactionType;
    }

    public BigDecimal getMaximumSingleAmount() {
        return maximumSingleAmount;
    }

    public BigDecimal getMaximumDailyAmount() {
        return maximumDailyAmount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasTransactionType(final AgentTransactionType transactionType) {
        return this.transactionType.equals(transactionType.getValue());
    }

    public void updateMaximumSingleAmount(final BigDecimal maximumSingleAmount) {
        this.maximumSingleAmount = maximumSingleAmount;
    }

    public void updateMaximumDailyAmount(final BigDecimal maximumDailyAmount) {
        this.maximumDailyAmount = maximumDailyAmount;
    }

    public void updateEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
