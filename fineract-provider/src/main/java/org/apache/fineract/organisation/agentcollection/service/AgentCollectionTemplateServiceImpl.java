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
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionTemplateData;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionLimit;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionData;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionData;

@RequiredArgsConstructor
public class AgentCollectionTemplateServiceImpl implements AgentCollectionTemplateService {

    private final AgentValidationService agentValidationService;
    private final AgentCollectionReadPlatformService agentCollectionReadPlatformService;

    @Override
    public Optional<SavingsAccountTransactionData> getSavingsDepositTemplateData(final SavingsAccountTransactionData transactionData,
            final Collection<PaymentTypeData> paymentTypeOptions) {
        return getTemplateData(AgentTransactionType.SAVINGS_DEPOSIT, transactionData.getOfficeId(), transactionData.getCurrency().getCode())
                .map(agentCollection -> {
                    transactionData.setAgentCollection(agentCollection);
                    return SavingsAccountTransactionData.templateOnTop(transactionData,
                            filterPaymentType(paymentTypeOptions, agentCollection.getPaymentTypeId()));
                });
    }

    @Override
    public Optional<LoanTransactionData> getLoanRepaymentTemplateData(final LoanTransactionData transactionData,
            final Collection<PaymentTypeData> paymentTypeOptions) {
        return getTemplateData(AgentTransactionType.LOAN_REPAYMENT, transactionData.getOfficeId(), transactionData.getCurrency().getCode())
                .map(agentCollection -> {
                    transactionData.setAgentCollection(agentCollection);
                    return LoanTransactionData.templateOnTop(transactionData,
                            filterPaymentType(paymentTypeOptions, agentCollection.getPaymentTypeId()));
                });
    }

    @Override
    public Optional<AgentCollectionTemplateData> getLoanRepaymentTemplateData(final Long accountOfficeId,
            final String accountCurrencyCode) {
        return getTemplateData(AgentTransactionType.LOAN_REPAYMENT, accountOfficeId, accountCurrencyCode);
    }

    @Override
    public Optional<AgentCollectionTemplateData> getSavingsDepositTemplateData(final Long accountOfficeId,
            final String accountCurrencyCode) {
        return getTemplateData(AgentTransactionType.SAVINGS_DEPOSIT, accountOfficeId, accountCurrencyCode);
    }

    public Collection<PaymentTypeData> filterPaymentType(final Collection<PaymentTypeData> paymentTypeOptions, final Long paymentTypeId) {
        return paymentTypeOptions.stream().filter(type -> Objects.equals(type.getId(), paymentTypeId)).toList();
    }

    private Optional<AgentCollectionTemplateData> getTemplateData(final AgentTransactionType transactionType, final Long accountOfficeId,
            final String accountCurrencyCode) {
        return this.agentValidationService.findActiveAgentForAuthenticatedUser()
                .map(agent -> buildTemplateData(agent, transactionType, accountOfficeId, accountCurrencyCode));
    }

    private AgentCollectionTemplateData buildTemplateData(final Agent agent, final AgentTransactionType transactionType,
            final Long accountOfficeId, final String accountCurrencyCode) {
        this.agentValidationService.validateAgentAccessToOffice(agent, accountOfficeId);
        this.agentValidationService.validateCurrency(agent, accountCurrencyCode);

        final PaymentType paymentType = agent.getPaymentType();
        if (paymentType == null || paymentType.getId() == null) {
            throw new AgentConfigurationException("payment.type.not.configured",
                    "The active agent does not have a configured payment type.", agent.getId());
        }

        final AgentTransactionLimit limit = this.agentValidationService.retrieveEnabledTransactionLimit(agent, transactionType);
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final BigDecimal currentCashInHand = this.agentCollectionReadPlatformService.retrieveCurrentCashInHand(agent.getId());
        final BigDecimal dailyTransactionTotal = this.agentCollectionReadPlatformService.retrieveDailyCollectionTotal(agent.getId(),
                transactionType, businessDate);
        final BigDecimal dailyTotal = this.agentCollectionReadPlatformService.retrieveDailyCollectionTotal(agent.getId(), businessDate);
        final BigDecimal remainingDailyTransactionLimit = nonNegative(limit.getMaximumDailyAmount().subtract(dailyTransactionTotal));
        final BigDecimal remainingDailyTotalLimit = nonNegative(agent.getMaximumDailyTotalCollection().subtract(dailyTotal));
        final BigDecimal remainingCashInHandCapacity = nonNegative(agent.getMaximumCashInHand().subtract(currentCashInHand));
        final BigDecimal effectiveMaximumCollectibleAmount = Stream
                .of(limit.getMaximumSingleAmount(), remainingDailyTransactionLimit, remainingDailyTotalLimit, remainingCashInHandCapacity)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

        return AgentCollectionTemplateData.builder().agentId(agent.getId()).transactionType(transactionType)
                .paymentTypeId(paymentType.getId()).paymentTypeName(paymentType.getName()).currencyCode(agent.getCurrencyCode())
                .maximumSingleAmount(limit.getMaximumSingleAmount()).remainingDailyTransactionLimit(remainingDailyTransactionLimit)
                .remainingDailyTotalLimit(remainingDailyTotalLimit).currentCashInHand(currentCashInHand)
                .remainingCashInHandCapacity(remainingCashInHandCapacity)
                .effectiveMaximumCollectibleAmount(effectiveMaximumCollectibleAmount).build();
    }

    private BigDecimal nonNegative(final BigDecimal amount) {
        return amount.signum() < 0 ? BigDecimal.ZERO : amount;
    }
}
