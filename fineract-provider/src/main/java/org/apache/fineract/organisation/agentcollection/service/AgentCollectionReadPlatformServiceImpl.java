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
package org.apache.fineract.organisation.agentcollection.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class AgentCollectionReadPlatformServiceImpl implements AgentCollectionReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public BigDecimal retrieveCurrentCashInHand(final Long agentId) {
        final String sql = """
                select coalesce(sum(act.amount), 0)
                from m_agent_collection_transaction act
                where act.agent_id = ?
                and act.status_enum in (?, ?)
                """;
        return zeroIfNull(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, agentId,
                AgentCollectionTransactionStatusType.PENDING.getValue(), AgentCollectionTransactionStatusType.DISPUTED.getValue()));
    }

    @Override
    public BigDecimal retrieveDailyCollectionTotal(final Long agentId, final LocalDate businessDate) {
        final String sql = """
                select coalesce(sum(act.amount), 0)
                from m_agent_collection_transaction act
                where act.agent_id = ?
                and act.transaction_date = ?
                and act.status_enum <> ?
                """;
        return zeroIfNull(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, agentId, businessDate,
                AgentCollectionTransactionStatusType.REVERSED.getValue()));
    }

    @Override
    public BigDecimal retrieveDailyCollectionTotal(final Long agentId, final AgentTransactionType transactionType,
            final LocalDate businessDate) {
        final String sql = """
                select coalesce(sum(act.amount), 0)
                from m_agent_collection_transaction act
                where act.agent_id = ?
                and act.transaction_type_enum = ?
                and act.transaction_date = ?
                and act.status_enum <> ?
                """;
        return zeroIfNull(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, agentId, transactionType.getValue(), businessDate,
                AgentCollectionTransactionStatusType.REVERSED.getValue()));
    }

    private BigDecimal zeroIfNull(final BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
