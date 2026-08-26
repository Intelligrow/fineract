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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionSummaryData;
import org.apache.fineract.organisation.agentcollection.data.AgentData;
import org.apache.fineract.organisation.agentcollection.data.AgentTransactionLimitData;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionStatusType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AgentCollectionReadPlatformServiceImplTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AgentCollectionReadPlatformServiceImpl service = new AgentCollectionReadPlatformServiceImpl(jdbcTemplate, null, null,
            null, null, null);

    @Test
    void retrieveCurrentCashInHand_countsOnlyPendingAndDisputedTransactions() {
        service.retrieveCurrentCashInHand(11L);

        verify(jdbcTemplate).queryForObject(contains("status_enum in (?, ?)"), eq(BigDecimal.class), eq(11L),
                eq(AgentCollectionTransactionStatusType.PENDING.getValue()), eq(AgentCollectionTransactionStatusType.DISPUTED.getValue()));
    }

    @Test
    void retrieveDailyCollectionTotal_excludesReversedTransactions() {
        final LocalDate businessDate = LocalDate.of(2026, 7, 30);

        service.retrieveDailyCollectionTotal(11L, businessDate);

        verify(jdbcTemplate).queryForObject(contains("status_enum <> ?"), eq(BigDecimal.class), eq(11L), eq(businessDate),
                eq(AgentCollectionTransactionStatusType.REVERSED.getValue()));
    }

    @Test
    void retrieveAgentSummary_calculatesDailyAndCapacityRemainingAmounts() {
        final AgentReadPlatformService agentReadPlatformService = mock(AgentReadPlatformService.class);
        final AgentCollectionReadPlatformServiceImpl summaryService = spy(
                new AgentCollectionReadPlatformServiceImpl(jdbcTemplate, null, agentReadPlatformService, null, null, null));
        final LocalDate businessDate = LocalDate.of(2026, 7, 31);
        final AgentData agent = AgentData.builder().id(11L).maximumCashInHand(new BigDecimal("500"))
                .maximumDailyTotalCollection(new BigDecimal("300"))
                .loanCollectionLimit(AgentTransactionLimitData.builder().maximumDailyAmount(new BigDecimal("200")).build())
                .savingsCollectionLimit(AgentTransactionLimitData.builder().maximumDailyAmount(new BigDecimal("150")).build()).build();
        when(agentReadPlatformService.retrieveAgent(11L)).thenReturn(agent);
        doReturn(new BigDecimal("75")).when(summaryService).retrieveCurrentCashInHand(11L);
        doReturn(new BigDecimal("40")).when(summaryService).retrieveDailyCollectionTotal(11L,
                org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType.LOAN_REPAYMENT, businessDate);
        doReturn(new BigDecimal("25")).when(summaryService).retrieveDailyCollectionTotal(11L,
                org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType.SAVINGS_DEPOSIT, businessDate);
        doReturn(new BigDecimal("65")).when(summaryService).retrieveDailyCollectionTotal(11L, businessDate);
        when(jdbcTemplate.queryForObject(anyString(), eq(BigDecimal.class), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(new BigDecimal("70"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(3L);
        when(jdbcTemplate.queryForObject(anyString(), eq(LocalDate.class), org.mockito.ArgumentMatchers.any()))
                .thenReturn(LocalDate.of(2026, 7, 30));

        final AgentCollectionSummaryData summary = summaryService.retrieveAgentSummary(11L, businessDate);

        org.assertj.core.api.Assertions.assertThat(summary.getCurrentCashInHand()).isEqualByComparingTo("75");
        org.assertj.core.api.Assertions.assertThat(summary.getTodaysLoanCollections()).isEqualByComparingTo("40");
        org.assertj.core.api.Assertions.assertThat(summary.getTodaysSavingsCollections()).isEqualByComparingTo("25");
        org.assertj.core.api.Assertions.assertThat(summary.getTodaysTotalCollections()).isEqualByComparingTo("65");
        org.assertj.core.api.Assertions.assertThat(summary.getPendingSettlementAmount()).isEqualByComparingTo("70");
        org.assertj.core.api.Assertions.assertThat(summary.getPendingTransactionCount()).isEqualTo(3L);
        org.assertj.core.api.Assertions.assertThat(summary.getRemainingLoanDailyLimit()).isEqualByComparingTo("160");
        org.assertj.core.api.Assertions.assertThat(summary.getRemainingSavingsDailyLimit()).isEqualByComparingTo("125");
        org.assertj.core.api.Assertions.assertThat(summary.getRemainingTotalDailyLimit()).isEqualByComparingTo("235");
        org.assertj.core.api.Assertions.assertThat(summary.getRemainingCashCapacity()).isEqualByComparingTo("425");
    }
}
