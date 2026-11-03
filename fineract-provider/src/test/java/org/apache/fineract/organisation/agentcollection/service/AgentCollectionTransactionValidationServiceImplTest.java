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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransaction;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionRepository;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionLimit;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.junit.jupiter.api.Test;

class AgentCollectionTransactionValidationServiceImplTest {

    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 7, 30);

    private final AgentValidationService agentValidationService = mock(AgentValidationService.class);
    private final AgentCollectionReadPlatformService agentCollectionReadPlatformService = mock(AgentCollectionReadPlatformService.class);
    private final AgentCollectionTransactionRepository agentCollectionTransactionRepository = mock(
            AgentCollectionTransactionRepository.class);
    private final AgentCollectionTransactionValidationServiceImpl service = new AgentCollectionTransactionValidationServiceImpl(
            agentValidationService, agentCollectionReadPlatformService, agentCollectionTransactionRepository);

    @Test
    void validateLoanRepaymentCollection_usesLoanCollectionLimit() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit loanLimit = limit(new BigDecimal("100"), new BigDecimal("300"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT)).thenReturn(loanLimit);
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.LOAN_REPAYMENT, TRANSACTION_DATE))
                .thenReturn(new BigDecimal("40"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, TRANSACTION_DATE)).thenReturn(new BigDecimal("60"));
        when(agentCollectionReadPlatformService.retrieveCurrentCashInHand(11L)).thenReturn(new BigDecimal("70"));

        assertDoesNotThrow(
                () -> service.validateLoanRepaymentCollection(agent, 7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE));

        verify(agentValidationService).validatePaymentType(agent, 2L);
        verify(agentValidationService).validateAgentAccessToOffice(agent, 7L);
        verify(agentValidationService).validateCurrency(agent, "USD");
        verify(agentValidationService).retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT);
        verify(agentValidationService, never()).retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT);
        verify(agentCollectionTransactionRepository).existsByLoanTransactionId(33L);
    }

    @Test
    void validateSavingsDepositCollection_usesSavingsCollectionLimit() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit savingsLimit = limit(new BigDecimal("80"), new BigDecimal("200"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT)).thenReturn(savingsLimit);
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.SAVINGS_DEPOSIT, TRANSACTION_DATE))
                .thenReturn(new BigDecimal("20"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, TRANSACTION_DATE)).thenReturn(new BigDecimal("40"));
        when(agentCollectionReadPlatformService.retrieveCurrentCashInHand(11L)).thenReturn(new BigDecimal("50"));

        assertDoesNotThrow(
                () -> service.validateSavingsDepositCollection(agent, 7L, "USD", 4L, 44L, 2L, new BigDecimal("60"), TRANSACTION_DATE));

        verify(agentValidationService).retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT);
        verify(agentValidationService, never()).retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT);
        verify(agentCollectionTransactionRepository).existsBySavingsTransactionId(44L);
    }

    @Test
    void validateLoanRepaymentCollection_rejectsExceededSingleLimit() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit loanLimit = limit(new BigDecimal("40"), new BigDecimal("300"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT)).thenReturn(loanLimit);

        final AgentConfigurationException exception = assertThrows(AgentConfigurationException.class,
                () -> service.validateLoanRepaymentCollection(agent, 7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE));

        assertEquals("error.msg.agent.loan.repayment.maximum.single.amount.exceeded", exception.getGlobalisationMessageCode());
        assertEquals("Loan repayment collection cannot be completed. Maximum single loan repayment limit reached. Attempted amount: 50; "
                + "maximum allowed per transaction: 40; remaining balance: 40.", exception.getDefaultUserMessage());
        assertArrayEquals(new Object[] { new BigDecimal("50"), new BigDecimal("40"), new BigDecimal("40") },
                exception.getDefaultUserMessageArgs());
    }

    @Test
    void validateSavingsDepositCollection_rejectsExceededSingleLimitWithRemainingBalanceMessage() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit savingsLimit = limit(new BigDecimal("40"), new BigDecimal("300"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT)).thenReturn(savingsLimit);

        final AgentConfigurationException exception = assertThrows(AgentConfigurationException.class,
                () -> service.validateSavingsDepositCollection(agent, 7L, "USD", 4L, 44L, 2L, new BigDecimal("50"), TRANSACTION_DATE));

        assertEquals("error.msg.agent.savings.deposit.maximum.single.amount.exceeded", exception.getGlobalisationMessageCode());
        assertEquals("Savings deposit collection cannot be completed. Maximum single savings deposit limit reached. Attempted amount: 50; "
                + "maximum allowed per transaction: 40; remaining balance: 40.", exception.getDefaultUserMessage());
        assertArrayEquals(new Object[] { new BigDecimal("50"), new BigDecimal("40"), new BigDecimal("40") },
                exception.getDefaultUserMessageArgs());
    }

    @Test
    void validateLoanRepaymentCollection_rejectsExceededLoanDailyLimitWithRemainingBalanceMessage() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit loanLimit = limit(new BigDecimal("100"), new BigDecimal("90"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT)).thenReturn(loanLimit);
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.LOAN_REPAYMENT, TRANSACTION_DATE))
                .thenReturn(new BigDecimal("60"));

        final AgentConfigurationException exception = assertThrows(AgentConfigurationException.class,
                () -> service.validateLoanRepaymentCollection(agent, 7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE));

        assertEquals("error.msg.agent.loan.repayment.maximum.daily.amount.exceeded", exception.getGlobalisationMessageCode());
        assertEquals("Loan repayment collection cannot be completed. Daily loan repayment limit reached. Attempted amount: 50; "
                + "already collected today: 60; daily limit: 90; remaining balance: 30.", exception.getDefaultUserMessage());
        assertArrayEquals(new Object[] { new BigDecimal("50"), new BigDecimal("60"), new BigDecimal("90"), new BigDecimal("30"),
                new BigDecimal("110") }, exception.getDefaultUserMessageArgs());
    }

    @Test
    void validateSavingsDepositCollection_rejectsExceededSavingsDailyLimitWithRemainingBalanceMessage() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit savingsLimit = limit(new BigDecimal("100"), new BigDecimal("80"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT)).thenReturn(savingsLimit);
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.SAVINGS_DEPOSIT, TRANSACTION_DATE))
                .thenReturn(new BigDecimal("65"));

        final AgentConfigurationException exception = assertThrows(AgentConfigurationException.class,
                () -> service.validateSavingsDepositCollection(agent, 7L, "USD", 4L, 44L, 2L, new BigDecimal("20"), TRANSACTION_DATE));

        assertEquals("error.msg.agent.savings.deposit.maximum.daily.amount.exceeded", exception.getGlobalisationMessageCode());
        assertEquals("Savings deposit collection cannot be completed. Daily savings deposit limit reached. Attempted amount: 20; "
                + "already collected today: 65; daily limit: 80; remaining balance: 15.", exception.getDefaultUserMessage());
        assertArrayEquals(new Object[] { new BigDecimal("20"), new BigDecimal("65"), new BigDecimal("80"), new BigDecimal("15"),
                new BigDecimal("85") }, exception.getDefaultUserMessageArgs());
    }

    @Test
    void validateLoanRepaymentCollection_rejectsExceededTotalDailyLimitWithRemainingBalanceMessage() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit loanLimit = limit(new BigDecimal("100"), new BigDecimal("300"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT)).thenReturn(loanLimit);
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.LOAN_REPAYMENT, TRANSACTION_DATE))
                .thenReturn(new BigDecimal("60"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, TRANSACTION_DATE)).thenReturn(new BigDecimal("480"));

        final AgentConfigurationException exception = assertThrows(AgentConfigurationException.class,
                () -> service.validateLoanRepaymentCollection(agent, 7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE));

        assertEquals("error.msg.agent.maximum.daily.total.amount.exceeded", exception.getGlobalisationMessageCode());
        assertEquals("Agent collection cannot be completed. Total daily collection limit reached. Attempted amount: 50; "
                + "already collected today: 480; daily limit: 500; remaining balance: 20.", exception.getDefaultUserMessage());
        assertArrayEquals(new Object[] { new BigDecimal("50"), new BigDecimal("480"), new BigDecimal("500"), new BigDecimal("20"),
                new BigDecimal("530") }, exception.getDefaultUserMessageArgs());
    }

    @Test
    void validateLoanRepaymentCollection_rejectsExceededCashInHandLimitWithRemainingBalanceMessage() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit loanLimit = limit(new BigDecimal("100"), new BigDecimal("300"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT)).thenReturn(loanLimit);
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.LOAN_REPAYMENT, TRANSACTION_DATE))
                .thenReturn(new BigDecimal("60"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, TRANSACTION_DATE)).thenReturn(new BigDecimal("70"));
        when(agentCollectionReadPlatformService.retrieveCurrentCashInHand(11L)).thenReturn(new BigDecimal("180"));

        final AgentConfigurationException exception = assertThrows(AgentConfigurationException.class,
                () -> service.validateLoanRepaymentCollection(agent, 7L, "USD", 3L, 33L, 2L, new BigDecimal("50"), TRANSACTION_DATE));

        assertEquals("error.msg.agent.maximum.cash.in.hand.amount.exceeded", exception.getGlobalisationMessageCode());
        assertEquals(
                "Agent collection cannot be completed. Maximum cash-in-hand limit reached. Attempted amount: 50; "
                        + "current cash in hand: 180; maximum cash in hand: 200; remaining balance: 20.",
                exception.getDefaultUserMessage());
        assertArrayEquals(new Object[] { new BigDecimal("50"), new BigDecimal("180"), new BigDecimal("200"), new BigDecimal("20"),
                new BigDecimal("230") }, exception.getDefaultUserMessageArgs());
    }

    @Test
    void validateSavingsDepositCollection_rejectsDuplicateSavingsTransaction() {
        final Agent agent = agent(11L);
        final AgentTransactionLimit savingsLimit = limit(new BigDecimal("80"), new BigDecimal("200"));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT)).thenReturn(savingsLimit);
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.SAVINGS_DEPOSIT, TRANSACTION_DATE))
                .thenReturn(new BigDecimal("20"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, TRANSACTION_DATE)).thenReturn(new BigDecimal("40"));
        when(agentCollectionReadPlatformService.retrieveCurrentCashInHand(11L)).thenReturn(new BigDecimal("50"));
        when(agentCollectionTransactionRepository.existsBySavingsTransactionId(44L)).thenReturn(true);

        assertThrows(AgentConfigurationException.class,
                () -> service.validateSavingsDepositCollection(agent, 7L, "USD", 4L, 44L, 2L, new BigDecimal("60"), TRANSACTION_DATE));
    }

    @Test
    void validateLoanRepaymentCollection_rejectsMissingLoanTransactionReferenceBeforeLimitChecks() {
        final Agent agent = agent(11L);

        assertThrows(AgentConfigurationException.class,
                () -> service.validateLoanRepaymentCollection(agent, 7L, "USD", 3L, null, 2L, new BigDecimal("50"), TRANSACTION_DATE));
        verifyNoInteractions(agentValidationService, agentCollectionReadPlatformService, agentCollectionTransactionRepository);
    }

    @Test
    void validateCollectionReversal_acceptsPendingTransaction() {
        final AgentCollectionTransaction transaction = transactionWithStatus(101L, AgentCollectionTransactionStatusType.PENDING);

        assertDoesNotThrow(() -> service.validateCollectionReversal(transaction));
    }

    @Test
    void validateCollectionReversal_rejectsSettledTransaction() {
        final AgentCollectionTransaction transaction = transactionWithStatus(101L, AgentCollectionTransactionStatusType.SETTLED);

        assertThrows(AgentConfigurationException.class, () -> service.validateCollectionReversal(transaction));
    }

    @Test
    void validateCollectionReversal_rejectsDuplicateReversal() {
        final AgentCollectionTransaction transaction = transactionWithStatus(101L, AgentCollectionTransactionStatusType.REVERSED);

        assertThrows(AgentConfigurationException.class, () -> service.validateCollectionReversal(transaction));
    }

    private Agent agent(final Long agentId) {
        final Agent agent = mock(Agent.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.status()).thenReturn(AgentStatusType.ACTIVE);
        when(agent.getMaximumCashInHand()).thenReturn(new BigDecimal("200"));
        when(agent.getMaximumDailyTotalCollection()).thenReturn(new BigDecimal("500"));
        return agent;
    }

    private AgentTransactionLimit limit(final BigDecimal maximumSingleAmount, final BigDecimal maximumDailyAmount) {
        final AgentTransactionLimit limit = mock(AgentTransactionLimit.class);
        when(limit.getMaximumSingleAmount()).thenReturn(maximumSingleAmount);
        when(limit.getMaximumDailyAmount()).thenReturn(maximumDailyAmount);
        return limit;
    }

    private AgentCollectionTransaction transactionWithStatus(final Long transactionId,
            final AgentCollectionTransactionStatusType statusType) {
        final AgentCollectionTransaction transaction = mock(AgentCollectionTransaction.class);
        when(transaction.getId()).thenReturn(transactionId);
        when(transaction.getStatus()).thenReturn(statusType.getValue());
        when(transaction.isPending()).thenReturn(statusType == AgentCollectionTransactionStatusType.PENDING);
        when(transaction.isSettled()).thenReturn(statusType == AgentCollectionTransactionStatusType.SETTLED);
        when(transaction.isReversed()).thenReturn(statusType == AgentCollectionTransactionStatusType.REVERSED);
        return transaction;
    }
}
