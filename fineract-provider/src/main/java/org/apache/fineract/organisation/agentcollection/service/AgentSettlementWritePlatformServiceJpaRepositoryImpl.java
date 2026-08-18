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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.glaccount.exception.GLAccountNotFoundException;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.agentcollection.data.AgentSettlementCommand;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransaction;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionRepository;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlement;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlementRepository;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class AgentSettlementWritePlatformServiceJpaRepositoryImpl implements AgentSettlementWritePlatformService {

    private final PlatformSecurityContext context;
    private final AgentValidationService agentValidationService;
    private final AgentSettlementValidationService agentSettlementValidationService;
    private final AgentSettlementRepository agentSettlementRepository;
    private final AgentCollectionTransactionRepository agentCollectionTransactionRepository;
    private final JournalEntryWritePlatformService journalEntryWritePlatformService;

    @Override
    @Transactional
    public CommandProcessingResult createDraftSettlement(final AgentSettlementCommand command) {
        final AppUser currentUser = this.context.authenticatedUser();
        final Agent agent = this.agentValidationService.findAgentLocked(command.getAgentId());
        this.agentValidationService.validateCurrentUserPrivilegeOnAgent(agent);
        validateCurrentUserIsSettlementAgent(agent, currentUser.getId());

        final List<AgentCollectionTransaction> transactions = this.agentCollectionTransactionRepository
                .findAllByIdInLocked(command.getTransactionIds());
        this.agentSettlementValidationService.validateDraftCreation(agent, command.getTransactionIds(), transactions,
                command.getSubmittedAmount());

        final LocalDate settlementDate = command.getSettlementDate() == null ? DateUtils.getBusinessLocalDate()
                : command.getSettlementDate();
        final BigDecimal expectedAmount = sum(transactions);
        final AgentSettlement settlement = AgentSettlement.draft(agent, settlementDate, expectedAmount, command.getSubmittedAmount(),
                command.getReferenceNumber(), command.getNotes());
        this.agentSettlementRepository.saveAndFlush(settlement);

        transactions.forEach(transaction -> transaction.linkToSettlement(settlement.getId()));
        this.agentCollectionTransactionRepository.saveAllAndFlush(transactions);

        return result(settlement, Map.of("status", "DRAFT", "expectedAmount", expectedAmount));
    }

    @Override
    @Transactional
    public CommandProcessingResult submitSettlement(final Long settlementId) {
        final AppUser currentUser = this.context.authenticatedUser();
        final AgentSettlement settlement = findSettlementLocked(settlementId);
        this.agentValidationService.validateCurrentUserPrivilegeOnAgent(settlement.getAgent());
        this.agentSettlementValidationService.validateSubmit(settlement, currentUser.getId());

        settlement.submit(currentUser.getId(), DateUtils.getBusinessLocalDate());
        this.agentSettlementRepository.saveAndFlush(settlement);
        return result(settlement, Map.of("status", "SUBMITTED"));
    }

    @Transactional
    @Override
    public CommandProcessingResult approveSettlement(final Long settlementId, JsonCommand command) {

        final AppUser currentUser = this.context.authenticatedUser();
        final AgentSettlement settlement = findByIdWithAgentAndOfficeLocked(settlementId);

        Agent agent = settlement.getAgent();
        this.agentValidationService.validateCurrentUserPrivilegeOnAgent(agent);

        Long creditGlAccount = command.longValueOfParameterNamed("glAccountId");
        if(creditGlAccount == null) {
            throw new GLAccountNotFoundException(creditGlAccount);
        }

        final List<AgentCollectionTransaction> transactions = this.agentCollectionTransactionRepository
                .findAllBySettlementIdLocked(settlement.getId());

        BigDecimal expectedAmount = sum(transactions);

        this.agentSettlementValidationService.validateApproval(settlement, transactions, currentUser.getId());

        transactions.forEach(transaction ->
                transaction.markAsSettled(settlement.getId(), settlement.getSettlementDate()));

        settlement.approve(currentUser.getId(), DateUtils.getBusinessLocalDate());

        this.journalEntryWritePlatformService.createJournalEntryForAgentSettlements(agent,expectedAmount,creditGlAccount);

        this.agentCollectionTransactionRepository.saveAllAndFlush(transactions);
        this.agentSettlementRepository.saveAndFlush(settlement);
        return result(settlement, Map.of("status", "APPROVED"));
    }

    @Override
    @Transactional
    public CommandProcessingResult rejectSettlement(final Long settlementId, final String rejectionReason) {
        final AppUser currentUser = this.context.authenticatedUser();
        final AgentSettlement settlement = findSettlementLocked(settlementId);
        this.agentValidationService.validateCurrentUserPrivilegeOnAgent(settlement.getAgent());
        this.agentSettlementValidationService.validateRejection(settlement, currentUser.getId(), rejectionReason);

        final List<AgentCollectionTransaction> transactions = this.agentCollectionTransactionRepository
                .findAllBySettlementIdLocked(settlement.getId());
        transactions.forEach(transaction -> transaction.releaseFromSettlement(settlement.getId()));
        settlement.reject(currentUser.getId(), DateUtils.getBusinessLocalDate(), rejectionReason);
        this.agentCollectionTransactionRepository.saveAllAndFlush(transactions);
        this.agentSettlementRepository.saveAndFlush(settlement);
        return result(settlement, Map.of("status", "REJECTED"));
    }

    @Override
    @Transactional
    public CommandProcessingResult cancelDraftSettlement(final Long settlementId) {
        final AppUser currentUser = this.context.authenticatedUser();
        final AgentSettlement settlement = findSettlementLocked(settlementId);
        this.agentValidationService.validateCurrentUserPrivilegeOnAgent(settlement.getAgent());
        this.agentSettlementValidationService.validateCancellation(settlement, currentUser.getId());

        final List<AgentCollectionTransaction> transactions = this.agentCollectionTransactionRepository
                .findAllBySettlementIdLocked(settlement.getId());
        transactions.forEach(transaction -> transaction.releaseFromSettlement(settlement.getId()));
        settlement.cancel(currentUser.getId(), DateUtils.getBusinessLocalDate());
        this.agentCollectionTransactionRepository.saveAllAndFlush(transactions);
        this.agentSettlementRepository.saveAndFlush(settlement);
        return result(settlement, Map.of("status", "CANCELLED"));
    }

    private AgentSettlement findSettlementLocked(final Long settlementId) {
        return this.agentSettlementRepository.findByIdWithAgentLocked(settlementId).orElseThrow(
                () -> new AgentConfigurationException("settlement.not.found", "Agent settlement was not found.", settlementId));
    }
    private AgentSettlement findByIdWithAgentAndOfficeLocked(final Long settlementId) {
        return this.agentSettlementRepository.findByIdWithAgentAndOfficeLocked(settlementId).orElseThrow(
                () -> new AgentConfigurationException("settlement.not.found", "Agent settlement was not found.", settlementId));
    }

    private void validateCurrentUserIsSettlementAgent(final Agent agent, final Long currentUserId) {
        if (agent.getAppUser() == null || !Objects.equals(agent.getAppUser().getId(), currentUserId)) {
            throw new AgentConfigurationException("settlement.user.not.agent",
                    "Only the configured agent user can create a settlement for the agent.", agent.getId(), currentUserId);
        }
    }

    private BigDecimal sum(final List<AgentCollectionTransaction> transactions) {
        return transactions.stream().map(AgentCollectionTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private CommandProcessingResult result(final AgentSettlement settlement, final Map<String, Object> changes) {
        final Map<String, Object> orderedChanges = new LinkedHashMap<>(changes);
        return new CommandProcessingResultBuilder().withEntityId(settlement.getId()).withOfficeId(settlement.getOfficeId())
                .with(orderedChanges).build();
    }
}
