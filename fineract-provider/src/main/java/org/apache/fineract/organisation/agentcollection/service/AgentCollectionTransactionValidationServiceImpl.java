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
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransaction;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionRepository;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionLimit;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;

@RequiredArgsConstructor
public class AgentCollectionTransactionValidationServiceImpl implements AgentCollectionTransactionValidationService {

    private final AgentValidationService agentValidationService;
    private final AgentCollectionReadPlatformService agentCollectionReadPlatformService;
    private final AgentCollectionTransactionRepository agentCollectionTransactionRepository;

    @Override
    public void validateLoanRepaymentCollection(final Agent agent, final Long accountOfficeId, final String accountCurrencyCode,
            final Long loanId, final Long loanTransactionId, final Long paymentTypeId, final BigDecimal amount,
            final LocalDate transactionDate) {
        validateRequiredId("loan.id.required", "Loan id is required for agent loan repayment collections.", loanId);
        validateRequiredId("loan.transaction.id.required", "Loan transaction id is required for agent loan repayment collections.",
                loanTransactionId);
        validateCollection(agent, AgentTransactionType.LOAN_REPAYMENT, accountOfficeId, accountCurrencyCode, paymentTypeId, amount,
                transactionDate);
        validateNoDuplicateLoanCollection(loanTransactionId);
    }

    @Override
    public void validateSavingsDepositCollection(final Agent agent, final Long accountOfficeId, final String accountCurrencyCode,
            final Long savingsAccountId, final Long savingsTransactionId, final Long paymentTypeId, final BigDecimal amount,
            final LocalDate transactionDate) {
        validateRequiredId("savings.account.id.required", "Savings account id is required for agent savings deposit collections.",
                savingsAccountId);
        validateRequiredId("savings.transaction.id.required", "Savings transaction id is required for agent savings deposit collections.",
                savingsTransactionId);
        validateCollection(agent, AgentTransactionType.SAVINGS_DEPOSIT, accountOfficeId, accountCurrencyCode, paymentTypeId, amount,
                transactionDate);
        validateNoDuplicateSavingsCollection(savingsTransactionId);
    }

    @Override
    public void validateCollectionReversal(final AgentCollectionTransaction transaction) {
        if (transaction.isSettled()) {
            throw new AgentConfigurationException("collection.transaction.already.settled",
                    "The agent collection transaction has already been settled and requires a separate recovery or adjustment workflow.",
                    transaction.getId());
        }
        if (transaction.isReversed()) {
            throw new AgentConfigurationException("collection.transaction.already.reversed",
                    "The agent collection transaction has already been reversed.", transaction.getId());
        }
        if (!transaction.isPending()) {
            throw new AgentConfigurationException("collection.transaction.status.invalid.for.reversal",
                    "Only pending agent collection transactions can be reversed.", transaction.getId(), transaction.getStatus());
        }
    }

    private void validateCollection(final Agent agent, final AgentTransactionType transactionType, final Long accountOfficeId,
            final String accountCurrencyCode, final Long paymentTypeId, final BigDecimal amount, final LocalDate transactionDate) {
        validateAmount(amount);
        this.agentValidationService.validatePaymentType(agent, paymentTypeId);
        this.agentValidationService.validateAgentAccessToOffice(agent, accountOfficeId);
        this.agentValidationService.validateCurrency(agent, accountCurrencyCode);

        final AgentTransactionLimit transactionLimit = this.agentValidationService.retrieveEnabledTransactionLimit(agent, transactionType);
        if(!transactionDate.equals(LocalDate.now())) {
            throw new
                    AgentConfigurationException("error.msg.agent.transaction.time.invalid",
                    "Backdated transactions are not Allowed" + transactionDate);
        }
        validateLimits(agent, transactionType, transactionLimit, amount, transactionDate);
    }

    private void validateRequiredId(final String code, final String message, final Long id) {
        if (id == null || id <= 0) {
            throw new AgentConfigurationException(code, message, id);
        }
    }

    private void validateAmount(final BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new AgentConfigurationException("amount.invalid", "Agent collection amount must be greater than zero.", amount);
        }
    }

    private void validateLimits(final Agent agent, final AgentTransactionType transactionType, final AgentTransactionLimit transactionLimit,
            final BigDecimal amount, final LocalDate transactionDate) {
        validateSingleTransactionLimit(transactionType, amount, transactionLimit.getMaximumSingleAmount());

        final LocalDate effectiveTransactionDate = transactionDate == null ? DateUtils.getBusinessLocalDate() : transactionDate;
        final BigDecimal dailyTransactionTotal = this.agentCollectionReadPlatformService.retrieveDailyCollectionTotal(agent.getId(),
                transactionType, effectiveTransactionDate);
        validateDailyTransactionLimit(transactionType, dailyTransactionTotal, amount, transactionLimit.getMaximumDailyAmount());

        final BigDecimal dailyTotal = this.agentCollectionReadPlatformService.retrieveDailyCollectionTotal(agent.getId(),
                effectiveTransactionDate);
        validateDailyTotalLimit(dailyTotal, amount, agent.getMaximumDailyTotalCollection());

        final BigDecimal currentCashInHand = this.agentCollectionReadPlatformService.retrieveCurrentCashInHand(agent.getId());
        validateCashInHandLimit(currentCashInHand, amount, agent.getMaximumCashInHand());
    }

    private void validateSingleTransactionLimit(final AgentTransactionType transactionType, final BigDecimal amount,
            final BigDecimal maximumAmount) {
        if (isLimitExceeded(amount, maximumAmount)) {
            final String transactionLabel = transactionLabel(transactionType);
            final String code = messageCodePrefix(transactionType) + ".maximum.single.amount.exceeded";
            throw new AgentConfigurationException(code,
                    sentenceCase(transactionLabel) + " collection cannot be completed. Maximum single " + transactionLabel
                            + " limit reached. Attempted amount: " + amount + "; maximum allowed per transaction: " + maximumAmount
                            + "; remaining balance: " + remainingBalance(BigDecimal.ZERO, maximumAmount) + ".",
                    amount, maximumAmount, remainingBalance(BigDecimal.ZERO, maximumAmount));
        }
    }

    private void validateDailyTransactionLimit(final AgentTransactionType transactionType, final BigDecimal currentAmount,
            final BigDecimal attemptedAmount, final BigDecimal maximumAmount) {
        final BigDecimal projectedAmount = currentAmount.add(attemptedAmount);
        if (isLimitExceeded(projectedAmount, maximumAmount)) {
            final String transactionLabel = transactionLabel(transactionType);
            final String code = messageCodePrefix(transactionType) + ".maximum.daily.amount.exceeded";
            throw new AgentConfigurationException(code,
                    sentenceCase(transactionLabel) + " collection cannot be completed. Daily " + transactionLabel
                            + " limit reached. Attempted amount: " + attemptedAmount + "; already collected today: " + currentAmount
                            + "; daily limit: " + maximumAmount + "; remaining balance: " + remainingBalance(currentAmount, maximumAmount)
                            + ".",
                    attemptedAmount, currentAmount, maximumAmount, remainingBalance(currentAmount, maximumAmount), projectedAmount);
        }
    }

    private void validateDailyTotalLimit(final BigDecimal currentAmount, final BigDecimal attemptedAmount, final BigDecimal maximumAmount) {
        final BigDecimal projectedAmount = currentAmount.add(attemptedAmount);
        if (isLimitExceeded(projectedAmount, maximumAmount)) {
            throw new AgentConfigurationException("maximum.daily.total.amount.exceeded",
                    "Agent collection cannot be completed. Total daily collection limit reached. Attempted amount: " + attemptedAmount
                            + "; already collected today: " + currentAmount + "; daily limit: " + maximumAmount + "; remaining balance: "
                            + remainingBalance(currentAmount, maximumAmount) + ".",
                    attemptedAmount, currentAmount, maximumAmount, remainingBalance(currentAmount, maximumAmount), projectedAmount);
        }
    }

    private void validateCashInHandLimit(final BigDecimal currentAmount, final BigDecimal attemptedAmount, final BigDecimal maximumAmount) {
        final BigDecimal projectedAmount = currentAmount.add(attemptedAmount);
        if (isLimitExceeded(projectedAmount, maximumAmount)) {
            throw new AgentConfigurationException("maximum.cash.in.hand.amount.exceeded",
                    "Agent collection cannot be completed. Maximum cash-in-hand limit reached. Attempted amount: " + attemptedAmount
                            + "; current cash in hand: " + currentAmount + "; maximum cash in hand: " + maximumAmount
                            + "; remaining balance: " + remainingBalance(currentAmount, maximumAmount) + ".",
                    attemptedAmount, currentAmount, maximumAmount, remainingBalance(currentAmount, maximumAmount), projectedAmount);
        }
    }

    private boolean isLimitExceeded(final BigDecimal projectedAmount, final BigDecimal maximumAmount) {
        return maximumAmount == null || projectedAmount.compareTo(maximumAmount) > 0;
    }

    private BigDecimal remainingBalance(final BigDecimal currentAmount, final BigDecimal maximumAmount) {
        if (maximumAmount == null) {
            return BigDecimal.ZERO;
        }
        final BigDecimal remainingAmount = maximumAmount.subtract(currentAmount);
        return remainingAmount.signum() < 0 ? BigDecimal.ZERO : remainingAmount;
    }

    private String transactionLabel(final AgentTransactionType transactionType) {
        return switch (transactionType) {
            case LOAN_REPAYMENT -> "loan repayment";
            case SAVINGS_DEPOSIT -> "savings deposit";
            default -> "agent collection";
        };
    }

    private String messageCodePrefix(final AgentTransactionType transactionType) {
        return switch (transactionType) {
            case LOAN_REPAYMENT -> "loan.repayment";
            case SAVINGS_DEPOSIT -> "savings.deposit";
            default -> "collection.transaction";
        };
    }

    private String sentenceCase(final String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void validateNoDuplicateLoanCollection(final Long loanTransactionId) {
        if (this.agentCollectionTransactionRepository.existsByLoanTransactionId(loanTransactionId)) {
            throw new AgentConfigurationException("loan.transaction.already.tracked",
                    "An agent collection transaction already exists for this loan transaction.", loanTransactionId);
        }
    }

    private void validateNoDuplicateSavingsCollection(final Long savingsTransactionId) {
        if (this.agentCollectionTransactionRepository.existsBySavingsTransactionId(savingsTransactionId)) {
            throw new AgentConfigurationException("savings.transaction.already.tracked",
                    "An agent collection transaction already exists for this savings transaction.", savingsTransactionId);
        }
    }
}
