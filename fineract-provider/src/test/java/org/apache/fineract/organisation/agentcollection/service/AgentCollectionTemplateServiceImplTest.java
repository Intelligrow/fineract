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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionTemplateData;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionLimit;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentCollectionTemplateServiceImplTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 30);

    private final AgentValidationService agentValidationService = mock(AgentValidationService.class);
    private final AgentCollectionReadPlatformService agentCollectionReadPlatformService = mock(AgentCollectionReadPlatformService.class);
    private final AgentCollectionTemplateServiceImpl service = new AgentCollectionTemplateServiceImpl(agentValidationService,
            agentCollectionReadPlatformService);

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void getSavingsDepositTemplateData_returnsEmptyForNonAgentUser() {
        when(agentValidationService.findActiveAgentForAuthenticatedUser()).thenReturn(Optional.empty());

        assertThat(service.getSavingsDepositTemplateData(7L, "USD")).isEmpty();
        verifyNoInteractions(agentCollectionReadPlatformService);
    }

    @Test
    void getSavingsDepositTemplateData_returnsSavingsMetadataForActiveAgent() {
        setBusinessDate();
        final Agent agent = agent(11L, 2L);
        final AgentTransactionLimit limit = limit(new BigDecimal("70"), new BigDecimal("120"));
        when(agentValidationService.findActiveAgentForAuthenticatedUser()).thenReturn(Optional.of(agent));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT)).thenReturn(limit);
        when(agentCollectionReadPlatformService.retrieveCurrentCashInHand(11L)).thenReturn(new BigDecimal("25"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.SAVINGS_DEPOSIT, BUSINESS_DATE))
                .thenReturn(new BigDecimal("30"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, BUSINESS_DATE)).thenReturn(new BigDecimal("40"));

        final AgentCollectionTemplateData result = service.getSavingsDepositTemplateData(7L, "USD").orElseThrow();

        assertThat(result.getAgentId()).isEqualTo(11L);
        assertThat(result.getTransactionType()).isEqualTo(AgentTransactionType.SAVINGS_DEPOSIT);
        assertThat(result.getPaymentTypeId()).isEqualTo(2L);
        assertThat(result.getPaymentTypeName()).isEqualTo("Agent Cash");
        assertThat(result.getCurrencyCode()).isEqualTo("USD");
        assertThat(result.getMaximumSingleAmount()).isEqualByComparingTo("70");
        assertThat(result.getRemainingDailyTransactionLimit()).isEqualByComparingTo("90");
        assertThat(result.getRemainingDailyTotalLimit()).isEqualByComparingTo("160");
        assertThat(result.getCurrentCashInHand()).isEqualByComparingTo("25");
        assertThat(result.getRemainingCashInHandCapacity()).isEqualByComparingTo("75");
        assertThat(result.getEffectiveMaximumCollectibleAmount()).isEqualByComparingTo("70");
        verify(agentValidationService).validateAgentAccessToOffice(agent, 7L);
        verify(agentValidationService).validateCurrency(agent, "USD");
        verify(agentValidationService).retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT);
        verify(agentValidationService, never()).retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT);
    }

    @Test
    void getSavingsDepositTemplateData_rejectsSavingsAccountOutsideAgentOffice() {
        final Agent agent = agent(11L, 2L);
        when(agentValidationService.findActiveAgentForAuthenticatedUser()).thenReturn(Optional.of(agent));
        doThrow(new AgentConfigurationException("office.outside.agent.office", "Savings office is outside agent office.", 99L))
                .when(agentValidationService).validateAgentAccessToOffice(agent, 99L);

        assertThrows(AgentConfigurationException.class, () -> service.getSavingsDepositTemplateData(99L, "USD"));
    }

    @Test
    void getLoanRepaymentTemplateData_usesLoanRepaymentTransactionLimit() {
        setBusinessDate();
        final Agent agent = agent(11L, 2L);
        final AgentTransactionLimit limit = limit(new BigDecimal("75"), new BigDecimal("90"));
        when(agentValidationService.findActiveAgentForAuthenticatedUser()).thenReturn(Optional.of(agent));
        when(agentValidationService.retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT)).thenReturn(limit);
        when(agentCollectionReadPlatformService.retrieveCurrentCashInHand(11L)).thenReturn(new BigDecimal("25"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, AgentTransactionType.LOAN_REPAYMENT, BUSINESS_DATE))
                .thenReturn(new BigDecimal("30"));
        when(agentCollectionReadPlatformService.retrieveDailyCollectionTotal(11L, BUSINESS_DATE)).thenReturn(new BigDecimal("40"));

        final AgentCollectionTemplateData result = service.getLoanRepaymentTemplateData(7L, "USD").orElseThrow();

        assertThat(result.getTransactionType()).isEqualTo(AgentTransactionType.LOAN_REPAYMENT);
        assertThat(result.getEffectiveMaximumCollectibleAmount()).isEqualByComparingTo("60");
        verify(agentValidationService).retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT);
        verify(agentValidationService, never()).retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT);
    }

    @Test
    void getSavingsDepositTemplateData_rejectsMissingAgentPaymentType() {
        final Agent agent = agent(11L, null);
        when(agentValidationService.findActiveAgentForAuthenticatedUser()).thenReturn(Optional.of(agent));

        assertThrows(AgentConfigurationException.class, () -> service.getSavingsDepositTemplateData(7L, "USD"));
    }

    @Test
    void getSavingsDepositTemplateData_rejectsInactiveAgentReturnedFromLookup() {
        final Agent agent = agent(11L, 2L);
        when(agent.status()).thenReturn(AgentStatusType.INACTIVE);
        when(agentValidationService.findActiveAgentForAuthenticatedUser()).thenReturn(Optional.of(agent));
        doThrow(new AgentConfigurationException("inactive", "Agent is not active.", 11L)).when(agentValidationService)
                .validateAgentAccessToOffice(agent, 7L);

        assertThrows(AgentConfigurationException.class, () -> service.getSavingsDepositTemplateData(7L, "USD"));
    }

    private void setBusinessDate() {
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
    }

    private Agent agent(final Long agentId, final Long paymentTypeId) {
        final Agent agent = mock(Agent.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.status()).thenReturn(AgentStatusType.ACTIVE);
        when(agent.getCurrencyCode()).thenReturn("USD");
        when(agent.getMaximumCashInHand()).thenReturn(new BigDecimal("100"));
        when(agent.getMaximumDailyTotalCollection()).thenReturn(new BigDecimal("200"));
        if (paymentTypeId != null) {
            final PaymentType paymentType = mock(PaymentType.class);
            when(paymentType.getId()).thenReturn(paymentTypeId);
            when(paymentType.getName()).thenReturn("Agent Cash");
            when(agent.getPaymentType()).thenReturn(paymentType);
        }
        return agent;
    }

    private AgentTransactionLimit limit(final BigDecimal maximumSingleAmount, final BigDecimal maximumDailyAmount) {
        final AgentTransactionLimit limit = mock(AgentTransactionLimit.class);
        when(limit.getMaximumSingleAmount()).thenReturn(maximumSingleAmount);
        when(limit.getMaximumDailyAmount()).thenReturn(maximumDailyAmount);
        when(limit.isEnabled()).thenReturn(true);
        return limit;
    }
}
