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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransaction;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlement;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AgentSettlementValidationServiceImplTest {

    private static final LocalDate SETTLEMENT_DATE = LocalDate.of(2026, 7, 30);

    private final AgentValidationService agentValidationService = mock(AgentValidationService.class);
    private final AgentSettlementValidationServiceImpl service = new AgentSettlementValidationServiceImpl(agentValidationService);

    @Test
    void validateDraftCreation_acceptsPendingUnlinkedTransactionsWhenAmountsMatch() {
        final Agent agent = agent(11L, 99L);
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));

        assertDoesNotThrow(() -> service.validateDraftCreation(agent, List.of(101L), List.of(transaction), new BigDecimal("50")));
    }

    @Test
    void validateDraftCreation_rejectsEmptySelection() {
        final Agent agent = agent(11L, 99L);

        assertThrows(AgentConfigurationException.class,
                () -> service.validateDraftCreation(agent, List.of(), List.of(), new BigDecimal("50")));
    }

    @Test
    void validateDraftCreation_rejectsMixedAgentTransactions() {
        final Agent agent = agent(11L, 99L);
        final Agent otherAgent = agent(12L, 100L);
        final AgentCollectionTransaction transaction = pendingTransaction(otherAgent, 101L, new BigDecimal("50"));

        assertThrows(AgentConfigurationException.class,
                () -> service.validateDraftCreation(agent, List.of(101L), List.of(transaction), new BigDecimal("50")));
    }

    @Test
    void validateDraftCreation_rejectsAlreadyLinkedTransaction() {
        final Agent agent = agent(11L, 99L);
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));
        transaction.linkToSettlement(77L);

        assertThrows(AgentConfigurationException.class,
                () -> service.validateDraftCreation(agent, List.of(101L), List.of(transaction), new BigDecimal("50")));
    }

    @Test
    void validateDraftCreation_rejectsReversedTransaction() {
        final Agent agent = agent(11L, 99L);
        final AgentCollectionTransaction transaction = transactionWithStatus(agent, 101L, AgentCollectionTransactionStatusType.REVERSED,
                new BigDecimal("50"));

        assertThrows(AgentConfigurationException.class,
                () -> service.validateDraftCreation(agent, List.of(101L), List.of(transaction), new BigDecimal("50")));
    }

    @Test
    void validateDraftCreation_rejectsAmountMismatch() {
        final Agent agent = agent(11L, 99L);
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));

        assertThrows(AgentConfigurationException.class,
                () -> service.validateDraftCreation(agent, List.of(101L), List.of(transaction), new BigDecimal("40")));
    }

    @Test
    void validateApproval_rejectsMakerApprovingOwnSettlement() {
        final Agent agent = agent(11L, 99L);
        final AgentSettlement settlement = submittedSettlement(agent, 55L, 99L, new BigDecimal("50"));
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));
        transaction.linkToSettlement(55L);

        assertThrows(AgentConfigurationException.class, () -> service.validateApproval(settlement, List.of(transaction), 99L));
    }

    @Test
    void validateApproval_acceptsSubmittedSettlementWithLinkedPendingTransactions() {
        final Agent agent = agent(11L, 99L);
        final AgentSettlement settlement = submittedSettlement(agent, 55L, 99L, new BigDecimal("50"));
        final AgentCollectionTransaction transaction = pendingTransaction(agent, 101L, new BigDecimal("50"));
        transaction.linkToSettlement(55L);

        assertDoesNotThrow(() -> service.validateApproval(settlement, List.of(transaction), 88L));
    }

    @Test
    void validateRejection_requiresReason() {
        final AgentSettlement settlement = submittedSettlement(agent(11L, 99L), 55L, 99L, new BigDecimal("50"));

        assertThrows(AgentConfigurationException.class, () -> service.validateRejection(settlement, 88L, ""));
    }

    private Agent agent(final Long agentId, final Long appUserId) {
        final Agent agent = mock(Agent.class);
        final Office office = mock(Office.class);
        final AppUser appUser = mock(AppUser.class);
        when(office.getId()).thenReturn(7L);
        when(appUser.getId()).thenReturn(appUserId);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getOffice()).thenReturn(office);
        when(agent.getAppUser()).thenReturn(appUser);
        when(agent.getCurrencyCode()).thenReturn("USD");
        return agent;
    }

    private AgentCollectionTransaction pendingTransaction(final Agent agent, final Long transactionId, final BigDecimal amount) {
        return transactionWithStatus(agent, transactionId, AgentCollectionTransactionStatusType.PENDING, amount);
    }

    private AgentCollectionTransaction transactionWithStatus(final Agent agent, final Long transactionId,
            final AgentCollectionTransactionStatusType status, final BigDecimal amount) {
        final AgentCollectionTransaction transaction = AgentCollectionTransaction.loanRepayment(agent, 7L, 3L, 33L, 2L, amount, "USD",
                SETTLEMENT_DATE, null, null);
        ReflectionTestUtils.setField(transaction, "id", transactionId);
        if (status == AgentCollectionTransactionStatusType.REVERSED) {
            transaction.markAsReversed();
        } else if (status == AgentCollectionTransactionStatusType.SETTLED) {
            transaction.markAsSettled(55L, SETTLEMENT_DATE);
        }
        return transaction;
    }

    private AgentSettlement submittedSettlement(final Agent agent, final Long settlementId, final Long submittedByUserId,
            final BigDecimal amount) {
        final AgentSettlement settlement = AgentSettlement.draft(agent, SETTLEMENT_DATE, amount, amount, "ref", "notes");
        ReflectionTestUtils.setField(settlement, "id", settlementId);
        settlement.submit(submittedByUserId, SETTLEMENT_DATE);
        return settlement;
    }
}
