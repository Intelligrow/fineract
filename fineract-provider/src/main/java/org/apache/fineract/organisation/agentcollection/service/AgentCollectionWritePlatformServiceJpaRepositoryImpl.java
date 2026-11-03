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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransaction;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionRepository;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class AgentCollectionWritePlatformServiceJpaRepositoryImpl implements AgentCollectionWritePlatformService {

    private final AgentValidationService agentValidationService;
    private final AgentCollectionTransactionValidationService agentCollectionTransactionValidationService;
    private final AgentCollectionTransactionRepository agentCollectionTransactionRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> recordLoanRepaymentCollection(final Long accountOfficeId, final String accountCurrencyCode, final Long loanId,
            final Long loanTransactionId, final Long paymentTypeId, final BigDecimal amount, final LocalDate transactionDate,
            final String externalId, final String mobileReference) {
        return this.agentValidationService.findActiveAgentForAuthenticatedUserLocked()
                .map(agent -> recordLoanRepaymentCollection(agent, accountOfficeId, accountCurrencyCode, loanId, loanTransactionId,
                        paymentTypeId, amount, transactionDate, externalId, mobileReference));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> recordSavingsDepositCollection(final Long accountOfficeId, final String accountCurrencyCode,
            final Long savingsAccountId, final Long savingsTransactionId, final Long paymentTypeId, final BigDecimal amount,
            final LocalDate transactionDate, final String externalId, final String mobileReference) {
        return this.agentValidationService.findActiveAgentForAuthenticatedUserLocked()
                .map(agent -> recordSavingsDepositCollection(agent, accountOfficeId, accountCurrencyCode, savingsAccountId,
                        savingsTransactionId, paymentTypeId, amount, transactionDate, externalId, mobileReference));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> recordSavingsDepositCollection(final SavingsAccountTransaction deposit) {
        final SavingsAccountTransactionType transactionType = deposit == null ? null : deposit.getTransactionType();
        if (transactionType == null || !transactionType.isDeposit() || deposit.isReversed()) {
            return Optional.empty();
        }
        return this.agentValidationService.findActiveAgentForAuthenticatedUserLocked()
                .map(agent -> recordSavingsDepositCollection(agent, deposit.getOfficeId(), deposit.getCurrency().getCode(),
                        deposit.getSavingsAccount().getId(), deposit.getId(), paymentTypeId(deposit.getPaymentDetail()),
                        deposit.getAmount(), deposit.getTransactionDate(), externalIdValue(deposit.getExternalId()), null));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> recordLoanRepaymentCollection(final LoanTransaction transaction) {
        if (transaction == null || !transaction.isRepayment()) {
            return Optional.empty();
        }
        return this.agentValidationService.findActiveAgentForAuthenticatedUserLocked()
                .map(agent -> recordLoanRepaymentCollection(agent, transaction.getOffice().getId(), transaction.getLoan().getCurrencyCode(),
                        transaction.getLoan().getId(), transaction.getId(), paymentTypeId(transaction.getPaymentDetail()), transaction.getAmount(),
                        transaction.getTransactionDate(), externalIdValue(transaction.getExternalId()), null));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> markLoanRepaymentCollectionReversed(final Long loanTransactionId) {
        if (loanTransactionId == null || loanTransactionId <= 0) {
            return Optional.empty();
        }
        return this.agentCollectionTransactionRepository.findByLoanTransactionId(loanTransactionId).map(this::markCollectionReversed);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Long> markSavingsDepositCollectionReversed(final Long savingsTransactionId) {
        if (savingsTransactionId == null || savingsTransactionId <= 0) {
            return Optional.empty();
        }
        return this.agentCollectionTransactionRepository.findBySavingsTransactionId(savingsTransactionId).map(this::markCollectionReversed);
    }

    private Long recordLoanRepaymentCollection(final Agent agent, final Long accountOfficeId, final String accountCurrencyCode,
            final Long loanId, final Long loanTransactionId, final Long paymentTypeId, final BigDecimal amount,
            final LocalDate transactionDate, final String externalId, final String mobileReference) {
        this.agentCollectionTransactionValidationService.validateLoanRepaymentCollection(agent, accountOfficeId, accountCurrencyCode,
                loanId, loanTransactionId, paymentTypeId, amount, transactionDate);
        final AgentCollectionTransaction transaction = createCollectionTransaction(agent, AgentTransactionType.LOAN_REPAYMENT,
                accountOfficeId, accountCurrencyCode, loanId, loanTransactionId, null, null, paymentTypeId, amount, transactionDate,
                externalId, mobileReference);
        return this.agentCollectionTransactionRepository.saveAndFlush(transaction).getId();
    }

    private Long recordSavingsDepositCollection(final Agent agent, final Long accountOfficeId, final String accountCurrencyCode,
            final Long savingsAccountId, final Long savingsTransactionId, final Long paymentTypeId, final BigDecimal amount,
            final LocalDate transactionDate, final String externalId, final String mobileReference) {
        this.agentCollectionTransactionValidationService.validateSavingsDepositCollection(agent, accountOfficeId, accountCurrencyCode,
                savingsAccountId, savingsTransactionId, paymentTypeId, amount, transactionDate);
        final AgentCollectionTransaction transaction = createCollectionTransaction(agent, AgentTransactionType.SAVINGS_DEPOSIT,
                accountOfficeId, accountCurrencyCode, null, null, savingsAccountId, savingsTransactionId, paymentTypeId, amount,
                transactionDate, externalId, mobileReference);
        return this.agentCollectionTransactionRepository.saveAndFlush(transaction).getId();
    }

    private Long markCollectionReversed(final AgentCollectionTransaction transaction) {
        this.agentCollectionTransactionValidationService.validateCollectionReversal(transaction);
        transaction.markAsReversed();
        return this.agentCollectionTransactionRepository.saveAndFlush(transaction).getId();
    }

    private AgentCollectionTransaction createCollectionTransaction(final Agent agent, final AgentTransactionType transactionType,
            final Long accountOfficeId, final String accountCurrencyCode, final Long loanId, final Long loanTransactionId,
            final Long savingsAccountId, final Long savingsTransactionId, final Long paymentTypeId, final BigDecimal amount,
            final LocalDate transactionDate, final String externalId, final String mobileReference) {
        final LocalDate effectiveTransactionDate = transactionDate == null ? DateUtils.getBusinessLocalDate() : transactionDate;
        return switch (transactionType) {
            case LOAN_REPAYMENT -> AgentCollectionTransaction.loanRepayment(agent, accountOfficeId, loanId, loanTransactionId,
                    paymentTypeId, amount, accountCurrencyCode, effectiveTransactionDate, externalId, mobileReference);
            case SAVINGS_DEPOSIT ->
                AgentCollectionTransaction.savingsDeposit(agent, accountOfficeId, savingsAccountId, savingsTransactionId, paymentTypeId,
                        amount, accountCurrencyCode, effectiveTransactionDate, externalId, mobileReference);
            default -> throw new AgentConfigurationException("transaction.type.invalid", "Unsupported agent collection transaction type.",
                    transactionType);
        };
    }

    private Long paymentTypeId(final PaymentDetail paymentDetail) {
        return paymentDetail == null || paymentDetail.getPaymentType() == null ? null : paymentDetail.getPaymentType().getId();
    }

    private String externalIdValue(final ExternalId externalId) {
        return externalId == null || externalId.isEmpty() ? null : externalId.getValue();
    }
}
