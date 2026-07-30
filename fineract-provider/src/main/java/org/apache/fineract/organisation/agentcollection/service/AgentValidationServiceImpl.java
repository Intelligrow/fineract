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

import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentRepositoryWrapper;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionLimit;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.useradministration.domain.AppUser;

@RequiredArgsConstructor
public class AgentValidationServiceImpl implements AgentValidationService {

    private final PlatformSecurityContext context;
    private final AgentRepositoryWrapper agentRepositoryWrapper;
    private final OfficeRepositoryWrapper officeRepositoryWrapper;

    @Override
    public Optional<Agent> findActiveAgentForAuthenticatedUser() {
        final AppUser user = this.context.authenticatedUser();
        return this.agentRepositoryWrapper.findActiveAgentByAppUserId(user.getId()).map(this::validateAgentIsActive);
    }

    @Override
    public Optional<Agent> findActiveAgentForAuthenticatedUserLocked() {
        final AppUser user = this.context.authenticatedUser();
        return this.agentRepositoryWrapper.findActiveAgentByAppUserIdLocked(user.getId()).map(this::validateAgentIsActive);
    }

    @Override
    public Agent findAgent(final Long agentId) {
        return this.agentRepositoryWrapper.findOneWithNotFoundDetection(agentId);
    }

    @Override
    public Agent findAgentLocked(final Long agentId) {
        return this.agentRepositoryWrapper.findOneWithNotFoundDetectionLocked(agentId);
    }

    @Override
    public Agent validateAgentIsActive(final Agent agent) {
        if (!agent.status().isActive()) {
            throw new AgentConfigurationException("inactive", "Agent " + agent.getId() + " is not active.", agent.getId());
        }
        return agent;
    }

    @Override
    public void validatePaymentType(final Agent agent, final Long paymentTypeId) {
        validateAgentIsActive(agent);
        if (paymentTypeId == null || agent.getPaymentType() == null || !Objects.equals(agent.getPaymentType().getId(), paymentTypeId)) {
            throw new AgentConfigurationException("payment.type.mismatch",
                    "The submitted payment type must match the payment type configured for the active agent.", paymentTypeId,
                    agent.getPaymentType() == null ? null : agent.getPaymentType().getId());
        }
    }

    @Override
    public void validateCurrency(final Agent agent, final String currencyCode) {
        validateAgentIsActive(agent);
        final String normalizedCurrencyCode = StringUtils.upperCase(StringUtils.trim(currencyCode));
        if (!Objects.equals(agent.getCurrencyCode(), normalizedCurrencyCode)) {
            throw new AgentConfigurationException("currency.mismatch",
                    "The account currency must match the currency configured for the active agent.", normalizedCurrencyCode,
                    agent.getCurrencyCode());
        }
    }

    @Override
    public AgentTransactionLimit retrieveTransactionLimit(final Agent agent, final AgentTransactionType transactionType) {
        validateAgentIsActive(agent);
        return agent.findTransactionLimit(transactionType)
                .orElseThrow(() -> new AgentConfigurationException("transaction.limit.not.configured",
                        "No collection limit is configured for transaction type " + transactionType + ".", transactionType));
    }

    @Override
    public AgentTransactionLimit retrieveEnabledTransactionLimit(final Agent agent, final AgentTransactionType transactionType) {
        final AgentTransactionLimit limit = retrieveTransactionLimit(agent, transactionType);
        if (!limit.isEnabled()) {
            throw new AgentConfigurationException("transaction.type.disabled",
                    "Agent collection is disabled for transaction type " + transactionType + ".", transactionType);
        }
        return limit;
    }

    @Override
    public Office validateUserPrivilegeOnOfficeAndRetrieve(final AppUser currentUser, final Long officeId) {
        final Office userOffice = this.officeRepositoryWrapper.findOfficeHierarchy(currentUser.getOffice().getId());
        if (userOffice.doesNotHaveAnOfficeInHierarchyWithId(officeId)) {
            throw new NoAuthorizationException("User does not have sufficient privileges to act on the provided office.");
        }
        return userOffice.identifiedBy(officeId) ? userOffice : this.officeRepositoryWrapper.findOfficeHierarchy(officeId);
    }

    @Override
    public Office validateCurrentUserPrivilegeOnOfficeAndRetrieve(final Long officeId) {
        return validateUserPrivilegeOnOfficeAndRetrieve(this.context.authenticatedUser(), officeId);
    }

    @Override
    public void validateCurrentUserPrivilegeOnAgent(final Agent agent) {
        validateCurrentUserPrivilegeOnOfficeAndRetrieve(agent.getOffice().getId());
    }

    @Override
    public void validateAgentAccessToOffice(final Agent agent, final Long officeId) {
        validateAgentIsActive(agent);
        final Office agentOffice = this.officeRepositoryWrapper.findOfficeHierarchy(agent.getOffice().getId());
        if (agentOffice.doesNotHaveAnOfficeInHierarchyWithId(officeId)) {
            throw new AgentConfigurationException("office.outside.agent.office",
                    "The target account office must be the agent office or a child office.", officeId, agentOffice.getId());
        }
    }

    @Override
    public void validateOfficeRelationships(final Office agentOffice, final AppUser appUser, final Staff staff) {
        if (agentOffice.doesNotHaveAnOfficeInHierarchyWithId(appUser.getOffice().getId())) {
            throw new AgentConfigurationException("app.user.office.outside.agent.office",
                    "The selected app user's office must be the agent office or a child office.", appUser.getOffice().getId(),
                    agentOffice.getId());
        }
        if (agentOffice.doesNotHaveAnOfficeInHierarchyWithId(staff.getOffice().getId())) {
            throw new AgentConfigurationException("staff.office.outside.agent.office",
                    "The selected staff member's office must be the agent office or a child office.", staff.getOffice().getId(),
                    agentOffice.getId());
        }
    }
}
