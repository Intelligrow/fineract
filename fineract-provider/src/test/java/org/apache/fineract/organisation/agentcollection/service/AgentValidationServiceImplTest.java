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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentRepositoryWrapper;
import org.apache.fineract.organisation.agentcollection.domain.AgentStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionLimit;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;

class AgentValidationServiceImplTest {

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final AgentRepositoryWrapper agentRepositoryWrapper = mock(AgentRepositoryWrapper.class);
    private final OfficeRepositoryWrapper officeRepositoryWrapper = mock(OfficeRepositoryWrapper.class);
    private final AgentValidationServiceImpl service = new AgentValidationServiceImpl(context, agentRepositoryWrapper,
            officeRepositoryWrapper);

    @Test
    void findActiveAgentForAuthenticatedUserLocked_returnsValidatedAgent() {
        final AppUser currentUser = mock(AppUser.class);
        final Agent agent = activeAgent();
        when(currentUser.getId()).thenReturn(10L);
        when(context.authenticatedUser()).thenReturn(currentUser);
        when(agentRepositoryWrapper.findAgentByAppUserIdLocked(10L)).thenReturn(Optional.of(agent));

        assertThat(service.findActiveAgentForAuthenticatedUserLocked()).containsSame(agent);
    }

    @Test
    void validateAgentIsActive_rejectsInactiveAgent() {
        final Agent agent = mock(Agent.class);
        when(agent.getId()).thenReturn(5L);
        when(agent.status()).thenReturn(AgentStatusType.INACTIVE);

        assertThrows(AgentConfigurationException.class, () -> service.validateAgentIsActive(agent));
    }

    @Test
    void validatePaymentType_rejectsMismatchedPaymentType() {
        final Agent agent = activeAgent();
        final PaymentType paymentType = mock(PaymentType.class);
        when(paymentType.getId()).thenReturn(7L);
        when(agent.getPaymentType()).thenReturn(paymentType);

        assertThrows(AgentConfigurationException.class, () -> service.validatePaymentType(agent, 9L));
    }

    @Test
    void validateCurrency_rejectsMismatchedCurrency() {
        final Agent agent = activeAgent();
        when(agent.getCurrencyCode()).thenReturn("USD");

        assertThrows(AgentConfigurationException.class, () -> service.validateCurrency(agent, "KES"));
    }

    @Test
    void retrieveEnabledTransactionLimit_returnsConfiguredEnabledLimit() {
        final Agent agent = activeAgent();
        final AgentTransactionLimit limit = mock(AgentTransactionLimit.class);
        when(limit.isEnabled()).thenReturn(true);
        when(agent.findTransactionLimit(AgentTransactionType.LOAN_REPAYMENT)).thenReturn(Optional.of(limit));

        assertThat(service.retrieveEnabledTransactionLimit(agent, AgentTransactionType.LOAN_REPAYMENT)).isSameAs(limit);
    }

    @Test
    void retrieveEnabledTransactionLimit_rejectsDisabledLimit() {
        final Agent agent = activeAgent();
        final AgentTransactionLimit limit = mock(AgentTransactionLimit.class);
        when(limit.isEnabled()).thenReturn(false);
        when(agent.findTransactionLimit(AgentTransactionType.SAVINGS_DEPOSIT)).thenReturn(Optional.of(limit));

        assertThrows(AgentConfigurationException.class,
                () -> service.retrieveEnabledTransactionLimit(agent, AgentTransactionType.SAVINGS_DEPOSIT));
    }

    @Test
    void validateUserPrivilegeOnOfficeAndRetrieve_rejectsOfficeOutsideUserHierarchy() {
        final AppUser currentUser = mock(AppUser.class);
        final Office currentUserOfficeReference = mock(Office.class);
        final Office userOfficeHierarchy = mock(Office.class);
        when(currentUser.getOffice()).thenReturn(currentUserOfficeReference);
        when(currentUserOfficeReference.getId()).thenReturn(1L);
        when(officeRepositoryWrapper.findOfficeHierarchy(1L)).thenReturn(userOfficeHierarchy);
        when(userOfficeHierarchy.doesNotHaveAnOfficeInHierarchyWithId(99L)).thenReturn(true);

        assertThrows(NoAuthorizationException.class, () -> service.validateUserPrivilegeOnOfficeAndRetrieve(currentUser, 99L));
    }

    private Agent activeAgent() {
        final Agent agent = mock(Agent.class);
        when(agent.getId()).thenReturn(5L);
        when(agent.status()).thenReturn(AgentStatusType.ACTIVE);
        return agent;
    }
}
