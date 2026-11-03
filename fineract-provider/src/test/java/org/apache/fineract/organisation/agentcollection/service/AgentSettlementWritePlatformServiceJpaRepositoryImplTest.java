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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.agentcollection.data.AgentSettlementCommand;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransaction;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionRepository;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlement;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlementRepository;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlementStatusType;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

class AgentSettlementWritePlatformServiceJpaRepositoryImplTest {

    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 7, 30);

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final AgentValidationService agentValidationService = mock(AgentValidationService.class);
    private final AgentSettlementValidationService agentSettlementValidationService = mock(AgentSettlementValidationService.class);
    private final AgentSettlementRepository agentSettlementRepository = mock(AgentSettlementRepository.class);
    private final AgentCollectionTransactionRepository agentCollectionTransactionRepository = mock(
            AgentCollectionTransactionRepository.class);
    private final JournalEntryWritePlatformService journalEntryWritePlatformService = mock(
            JournalEntryWritePlatformService.class);
    private final AgentSettlementWritePlatformServiceJpaRepositoryImpl service = new AgentSettlementWritePlatformServiceJpaRepositoryImpl(
            context, agentValidationService, agentSettlementValidationService, agentSettlementRepository,
            agentCollectionTransactionRepository,journalEntryWritePlatformService);

    @Test
    void createDraftSettlement_linksSelectedTransactions() {
        final Agent agent = agent(11L, 99L);
        final AppUser currentUser = user(99L);
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));
        final AgentSettlementCommand command = AgentSettlementCommand.builder().agentId(11L).transactionIds(List.of(101L))
                .settlementDate(SETTLEMENT_DATE).submittedAmount(new BigDecimal("50")).referenceNumber("ref").notes("notes").build();
        when(context.authenticatedUser()).thenReturn(currentUser);
        when(agentValidationService.findAgentLocked(11L)).thenReturn(agent);
        when(agentCollectionTransactionRepository.findAllByIdInLocked(List.of(101L))).thenReturn(List.of(transaction));
        when(agentSettlementRepository.saveAndFlush(any(AgentSettlement.class))).thenAnswer(invocation -> {
            final AgentSettlement settlement = invocation.getArgument(0);
            ReflectionTestUtils.setField(settlement, "id", 55L);
            return settlement;
        });

        final CommandProcessingResult result = service.createDraftSettlement(command);

        assertThat(result.getResourceId()).isEqualTo(55L);
        assertThat(transaction.getSettlementId()).isEqualTo(55L);
        verify(agentSettlementValidationService).validateDraftCreation(agent, List.of(101L), List.of(transaction), new BigDecimal("50"));
        verify(agentCollectionTransactionRepository).saveAllAndFlush(List.of(transaction));
    }

    @Test
    void approveSettlement_marksLinkedPendingTransactionsSettled() {
        final Agent agent = agent(11L, 99L);
        final AgentSettlement settlement = submittedSettlement(agent, 55L, 99L, new BigDecimal("50"));
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));
        final AppUser reviewer = user(88L);
        transaction.linkToSettlement(55L);
        when(context.authenticatedUser()).thenReturn(reviewer);
        when(agentSettlementRepository.findByIdWithAgentLocked(55L)).thenReturn(Optional.of(settlement));
        when(agentCollectionTransactionRepository.findAllBySettlementIdLocked(55L)).thenReturn(List.of(transaction));

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class)) {
            dateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(SETTLEMENT_DATE);
            service.approveSettlement(55L, JsonCommand.from("{'glAccountId':1}"));
        }

        assertThat(transaction.getStatus()).isEqualTo(AgentCollectionTransactionStatusType.SETTLED.getValue());
        assertThat(transaction.getSettledDate()).isEqualTo(SETTLEMENT_DATE);
        assertThat(settlement.getStatus()).isEqualTo(AgentSettlementStatusType.APPROVED.getValue());
        verify(agentSettlementValidationService).validateApproval(settlement, List.of(transaction), 88L);
        verify(agentCollectionTransactionRepository).saveAllAndFlush(List.of(transaction));
        verify(agentSettlementRepository).saveAndFlush(settlement);
    }

    @Test
    void rejectSettlement_releasesSelectedTransactions() {
        final Agent agent = agent(11L, 99L);
        final AgentSettlement settlement = submittedSettlement(agent, 55L, 99L, new BigDecimal("50"));
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));
        final AppUser reviewer = user(88L);
        transaction.linkToSettlement(55L);
        when(context.authenticatedUser()).thenReturn(reviewer);
        when(agentSettlementRepository.findByIdWithAgentLocked(55L)).thenReturn(Optional.of(settlement));
        when(agentCollectionTransactionRepository.findAllBySettlementIdLocked(55L)).thenReturn(List.of(transaction));

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class)) {
            dateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(SETTLEMENT_DATE);
            service.rejectSettlement(55L, "cash mismatch");
        }

        assertThat(transaction.getSettlementId()).isNull();
        assertThat(settlement.getStatus()).isEqualTo(AgentSettlementStatusType.REJECTED.getValue());
        assertThat(settlement.getRejectionReason()).isEqualTo("cash mismatch");
    }

    @Test
    void cancelDraftSettlement_releasesSelectedTransactions() {
        final Agent agent = agent(11L, 99L);
        final AgentSettlement settlement = draftSettlement(agent, 55L, new BigDecimal("50"));
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));
        final AppUser maker = user(99L);
        transaction.linkToSettlement(55L);
        when(context.authenticatedUser()).thenReturn(maker);
        when(agentSettlementRepository.findByIdWithAgentLocked(55L)).thenReturn(Optional.of(settlement));
        when(agentCollectionTransactionRepository.findAllBySettlementIdLocked(55L)).thenReturn(List.of(transaction));

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class)) {
            dateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(SETTLEMENT_DATE);
            service.cancelDraftSettlement(55L);
        }

        assertThat(transaction.getSettlementId()).isNull();
        assertThat(settlement.getStatus()).isEqualTo(AgentSettlementStatusType.CANCELLED.getValue());
    }

    @Test
    void submitSettlement_marksDraftSubmittedByCurrentUser() {
        final Agent agent = agent(11L, 99L);
        final AgentSettlement settlement = draftSettlement(agent, 55L, new BigDecimal("50"));
        final AppUser maker = user(99L);
        when(context.authenticatedUser()).thenReturn(maker);
        when(agentSettlementRepository.findByIdWithAgentLocked(55L)).thenReturn(Optional.of(settlement));

        try (MockedStatic<DateUtils> dateUtils = mockStatic(DateUtils.class)) {
            dateUtils.when(DateUtils::getBusinessLocalDate).thenReturn(SETTLEMENT_DATE);
            service.submitSettlement(55L);
        }

        assertThat(settlement.getStatus()).isEqualTo(AgentSettlementStatusType.SUBMITTED.getValue());
        assertThat(settlement.getSubmittedByUserId()).isEqualTo(99L);
        verify(agentSettlementValidationService).validateSubmit(settlement, 99L);
        verify(agentSettlementRepository).saveAndFlush(settlement);
    }

    private Agent agent(final Long agentId, final Long appUserId) {
        final Agent agent = mock(Agent.class);
        final Office office = mock(Office.class);
        final AppUser appUser = user(appUserId);
        when(office.getId()).thenReturn(7L);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getOffice()).thenReturn(office);
        when(agent.getAppUser()).thenReturn(appUser);
        when(agent.getCurrencyCode()).thenReturn("USD");
        return agent;
    }

    private AppUser user(final Long userId) {
        final AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(userId);
        return user;
    }

    private AgentCollectionTransaction pendingTransaction(final Agent agent, final Long transactionId, final BigDecimal amount) {
        final AgentCollectionTransaction transaction = AgentCollectionTransaction.loanRepayment(agent, 7L, 3L, 33L, 2L, amount, "USD",
                SETTLEMENT_DATE, null, null);
        ReflectionTestUtils.setField(transaction, "id", transactionId);
        return transaction;
    }

    private AgentSettlement draftSettlement(final Agent agent, final Long settlementId, final BigDecimal amount) {
        final AgentSettlement settlement = AgentSettlement.draft(agent, SETTLEMENT_DATE, amount, amount, "ref", "notes");
        ReflectionTestUtils.setField(settlement, "id", settlementId);
        return settlement;
    }

    private AgentSettlement submittedSettlement(final Agent agent, final Long settlementId, final Long submittedByUserId,
            final BigDecimal amount) {
        final AgentSettlement settlement = draftSettlement(agent, settlementId, amount);
        settlement.submit(submittedByUserId, SETTLEMENT_DATE);
        return settlement;
    }
}
