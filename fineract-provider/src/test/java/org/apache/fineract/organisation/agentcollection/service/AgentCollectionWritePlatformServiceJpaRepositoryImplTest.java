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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransaction;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionRepository;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentCollectionWritePlatformServiceJpaRepositoryImplTest {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 7, 30);

    private final AgentValidationService agentValidationService = mock(AgentValidationService.class);
    private final AgentCollectionTransactionValidationService agentCollectionTransactionValidationService = mock(
            AgentCollectionTransactionValidationService.class);
    private final AgentCollectionTransactionRepository agentCollectionTransactionRepository = mock(
            AgentCollectionTransactionRepository.class);
    private final AgentCollectionWritePlatformServiceJpaRepositoryImpl service = new AgentCollectionWritePlatformServiceJpaRepositoryImpl(
            agentValidationService, agentCollectionTransactionValidationService, agentCollectionTransactionRepository);

    @Test
    void recordLoanRepaymentCollection_returnsEmptyForNonAgentUser() {
        when(agentValidationService.findActiveAgentForAuthenticatedUserLocked()).thenReturn(Optional.empty());

        assertThat(service.recordLoanRepaymentCollection(7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE, null, null))
                .isEmpty();

        verifyNoInteractions(agentCollectionTransactionValidationService, agentCollectionTransactionRepository);
    }

    @Test
    void recordLoanRepaymentCollection_validatesAndCreatesPendingTransaction() {
        final Agent agent = agent(11L);
        final AgentCollectionTransaction savedTransaction = savedTransaction(101L);
        when(agentValidationService.findActiveAgentForAuthenticatedUserLocked()).thenReturn(Optional.of(agent));
        when(agentCollectionTransactionRepository.saveAndFlush(any(AgentCollectionTransaction.class))).thenReturn(savedTransaction);

        final Optional<Long> result = service.recordLoanRepaymentCollection(7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE,
                "ext-1", "mobile-1");

        assertThat(result).contains(101L);
        verify(agentCollectionTransactionValidationService).validateLoanRepaymentCollection(agent, 7L, "USD", 3L, 33L, 2L,
                new BigDecimal("50"), TRANSACTION_DATE);

        final ArgumentCaptor<AgentCollectionTransaction> transactionCaptor = ArgumentCaptor.forClass(AgentCollectionTransaction.class);
        verify(agentCollectionTransactionRepository).saveAndFlush(transactionCaptor.capture());
        final AgentCollectionTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getAgentId()).isEqualTo(11L);
        assertThat(transaction.getTransactionType()).isEqualTo(AgentTransactionType.LOAN_REPAYMENT.getValue());
        assertThat(transaction.getLoanId()).isEqualTo(3L);
        assertThat(transaction.getLoanTransactionId()).isEqualTo(33L);
        assertThat(transaction.getSavingsAccountId()).isNull();
        assertThat(transaction.getPaymentTypeId()).isEqualTo(2L);
        assertThat(transaction.getAmount()).isEqualByComparingTo("50");
        assertThat(transaction.getCurrencyCode()).isEqualTo("USD");
        assertThat(transaction.getStatus()).isEqualTo(AgentCollectionTransactionStatusType.PENDING.getValue());
    }

    @Test
    void recordSavingsDepositCollection_validatesAndCreatesPendingTransaction() {
        final Agent agent = agent(11L);
        final AgentCollectionTransaction savedTransaction = savedTransaction(102L);
        when(agentValidationService.findActiveAgentForAuthenticatedUserLocked()).thenReturn(Optional.of(agent));
        when(agentCollectionTransactionRepository.saveAndFlush(any(AgentCollectionTransaction.class))).thenReturn(savedTransaction);

        final Optional<Long> result = service.recordSavingsDepositCollection(7L, "USD", 4L, 44L, 2L, new BigDecimal("60"), TRANSACTION_DATE,
                "ext-2", "mobile-2");

        assertThat(result).contains(102L);
        verify(agentCollectionTransactionValidationService).validateSavingsDepositCollection(agent, 7L, "USD", 4L, 44L, 2L,
                new BigDecimal("60"), TRANSACTION_DATE);

        final ArgumentCaptor<AgentCollectionTransaction> transactionCaptor = ArgumentCaptor.forClass(AgentCollectionTransaction.class);
        verify(agentCollectionTransactionRepository).saveAndFlush(transactionCaptor.capture());
        final AgentCollectionTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo(AgentTransactionType.SAVINGS_DEPOSIT.getValue());
        assertThat(transaction.getLoanId()).isNull();
        assertThat(transaction.getSavingsAccountId()).isEqualTo(4L);
        assertThat(transaction.getSavingsTransactionId()).isEqualTo(44L);
        assertThat(transaction.getAmount()).isEqualByComparingTo("60");
    }

    @Test
    void recordLoanRepaymentCollection_doesNotSaveWhenValidationFails() {
        final Agent agent = agent(11L);
        when(agentValidationService.findActiveAgentForAuthenticatedUserLocked()).thenReturn(Optional.of(agent));
        doThrow(new AgentConfigurationException("amount.invalid", "Invalid amount.")).when(agentCollectionTransactionValidationService)
                .validateLoanRepaymentCollection(agent, 7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE);

        assertThrows(AgentConfigurationException.class,
                () -> service.recordLoanRepaymentCollection(7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE, null, null));
        verify(agentCollectionTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void recordLoanRepaymentCollection_returnsEmptyForNonRepaymentSourceTransaction() {
        final LoanTransaction loanTransaction = mock(LoanTransaction.class);
        when(loanTransaction.isRepayment()).thenReturn(false);

        assertThat(service.recordLoanRepaymentCollection(loanTransaction)).isEmpty();

        verifyNoInteractions(agentValidationService, agentCollectionTransactionValidationService, agentCollectionTransactionRepository);
    }

    @Test
    void recordSavingsDepositCollection_returnsEmptyForNonDepositSourceTransaction() {
        final SavingsAccountTransaction savingsTransaction = mock(SavingsAccountTransaction.class);
        when(savingsTransaction.getTransactionType()).thenReturn(SavingsAccountTransactionType.WITHDRAWAL);

        assertThat(service.recordSavingsDepositCollection(savingsTransaction)).isEmpty();

        verifyNoInteractions(agentValidationService, agentCollectionTransactionValidationService, agentCollectionTransactionRepository);
    }

    @Test
    void markLoanRepaymentCollectionReversed_marksPendingTransactionReversed() {
        final AgentCollectionTransaction transaction = AgentCollectionTransaction.loanRepayment(agent(11L), 7L, 3L, 33L, 2L,
                new BigDecimal("50"), "USD", TRANSACTION_DATE, "ext-1", "mobile-1");
        final AgentCollectionTransaction savedTransaction = savedTransaction(101L);
        when(agentCollectionTransactionRepository.findByLoanTransactionId(33L)).thenReturn(Optional.of(transaction));
        when(agentCollectionTransactionRepository.saveAndFlush(transaction)).thenReturn(savedTransaction);

        final Optional<Long> result = service.markLoanRepaymentCollectionReversed(33L);

        assertThat(result).contains(101L);
        assertThat(transaction.getStatus()).isEqualTo(AgentCollectionTransactionStatusType.REVERSED.getValue());
        verify(agentCollectionTransactionValidationService).validateCollectionReversal(transaction);
        verify(agentCollectionTransactionRepository).saveAndFlush(transaction);
    }

    @Test
    void markSavingsDepositCollectionReversed_marksPendingTransactionReversed() {
        final AgentCollectionTransaction transaction = AgentCollectionTransaction.savingsDeposit(agent(11L), 7L, 4L, 44L, 2L,
                new BigDecimal("60"), "USD", TRANSACTION_DATE, "ext-2", "mobile-2");
        final AgentCollectionTransaction savedTransaction = savedTransaction(102L);
        when(agentCollectionTransactionRepository.findBySavingsTransactionId(44L)).thenReturn(Optional.of(transaction));
        when(agentCollectionTransactionRepository.saveAndFlush(transaction)).thenReturn(savedTransaction);

        final Optional<Long> result = service.markSavingsDepositCollectionReversed(44L);

        assertThat(result).contains(102L);
        assertThat(transaction.getStatus()).isEqualTo(AgentCollectionTransactionStatusType.REVERSED.getValue());
        verify(agentCollectionTransactionValidationService).validateCollectionReversal(transaction);
        verify(agentCollectionTransactionRepository).saveAndFlush(transaction);
    }

    @Test
    void markLoanRepaymentCollectionReversed_returnsEmptyWhenCollectionDoesNotExist() {
        when(agentCollectionTransactionRepository.findByLoanTransactionId(33L)).thenReturn(Optional.empty());

        assertThat(service.markLoanRepaymentCollectionReversed(33L)).isEmpty();

        verifyNoInteractions(agentValidationService, agentCollectionTransactionValidationService);
        verify(agentCollectionTransactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void markLoanRepaymentCollectionReversed_returnsEmptyForMissingTransactionId() {
        assertThat(service.markLoanRepaymentCollectionReversed(null)).isEmpty();

        verifyNoInteractions(agentValidationService, agentCollectionTransactionValidationService, agentCollectionTransactionRepository);
    }

    @Test
    void markSavingsDepositCollectionReversed_returnsEmptyForMissingTransactionId() {
        assertThat(service.markSavingsDepositCollectionReversed(null)).isEmpty();

        verifyNoInteractions(agentValidationService, agentCollectionTransactionValidationService, agentCollectionTransactionRepository);
    }

    @Test
    void markLoanRepaymentCollectionReversed_doesNotSaveWhenValidationFails() {
        final AgentCollectionTransaction transaction = AgentCollectionTransaction.loanRepayment(agent(11L), 7L, 3L, 33L, 2L,
                new BigDecimal("50"), "USD", TRANSACTION_DATE, "ext-1", "mobile-1");
        when(agentCollectionTransactionRepository.findByLoanTransactionId(33L)).thenReturn(Optional.of(transaction));
        doThrow(new AgentConfigurationException("collection.transaction.already.settled",
                "The agent collection transaction has already been settled.")).when(agentCollectionTransactionValidationService)
                .validateCollectionReversal(transaction);

        assertThrows(AgentConfigurationException.class, () -> service.markLoanRepaymentCollectionReversed(33L));
        assertThat(transaction.getStatus()).isEqualTo(AgentCollectionTransactionStatusType.PENDING.getValue());
        verify(agentCollectionTransactionRepository, never()).saveAndFlush(any());
    }

    private Agent agent(final Long agentId) {
        final Agent agent = mock(Agent.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.status()).thenReturn(AgentStatusType.ACTIVE);
        return agent;
    }

    private AgentCollectionTransaction savedTransaction(final Long id) {
        final AgentCollectionTransaction transaction = mock(AgentCollectionTransaction.class);
        when(transaction.getId()).thenReturn(id);
        return transaction;
    }
}
