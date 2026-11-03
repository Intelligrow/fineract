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

import java.util.Optional;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionLimit;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.useradministration.domain.AppUser;

public interface AgentValidationService {

    Optional<Agent> findActiveAgentForAuthenticatedUser();

    Optional<Agent> findActiveAgentForAuthenticatedUserLocked();

    Agent findAgent(Long agentId);

    Agent findAgentLocked(Long agentId);

    Agent validateAgentIsActive(Agent agent);

    void validatePaymentType(Agent agent, Long paymentTypeId);

    void validateCurrency(Agent agent, String currencyCode);

    AgentTransactionLimit retrieveTransactionLimit(Agent agent, AgentTransactionType transactionType);

    AgentTransactionLimit retrieveEnabledTransactionLimit(Agent agent, AgentTransactionType transactionType);

    Office validateUserPrivilegeOnOfficeAndRetrieve(AppUser currentUser, Long officeId);

    Office validateCurrentUserPrivilegeOnOfficeAndRetrieve(Long officeId);

    void validateCurrentUserPrivilegeOnAgent(Agent agent);

    void validateAgentAccessToOffice(Agent agent, Long officeId);

    void validateOfficeRelationships(Office agentOffice, AppUser appUser, Staff staff);
}
