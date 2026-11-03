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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransaction;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlement;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;

@RequiredArgsConstructor
public class AgentSettlementValidationServiceImpl implements AgentSettlementValidationService {

    private final AgentValidationService agentValidationService;

    @Override
    public void validateDraftCreation(final Agent agent, final List<Long> requestedTransactionIds,
            final List<AgentCollectionTransaction> transactions, final BigDecimal submittedAmount) {
        this.agentValidationService.validateAgentIsActive(agent);
        validateTransactionSelection(requestedTransactionIds, transactions);
        validateSelectedTransactionsForDraft(agent, transactions);
        validateSubmittedAmount(sum(transactions), submittedAmount);
    }

    @Override
    public void validateSubmit(final AgentSettlement settlement, final Long currentUserId) {
        validateSettlementExists(settlement);
        if (!settlement.isDraft()) {
            throw new AgentConfigurationException("settlement.status.invalid.for.submit", "Only draft settlements can be submitted.",
                    settlement.getId(), settlement.getStatus());
        }
        validateMaker(settlement, currentUserId);
    }

    @Override
    public void validateApproval(final AgentSettlement settlement, final List<AgentCollectionTransaction> transactions,
            final Long currentUserId) {
        validateSettlementExists(settlement);
        if (!settlement.isSubmitted()) {
            throw new AgentConfigurationException("settlement.status.invalid.for.approval", "Only submitted settlements can be approved.",
                    settlement.getId(), settlement.getStatus());
        }
        validateNotMaker(settlement, currentUserId, "approve");
        validateTransactionSelectionForExistingSettlement(settlement, transactions);
        validateSubmittedAmount(sum(transactions), settlement.getExpectedAmount());
        validateSubmittedAmount(sum(transactions), settlement.getSubmittedAmount());
    }

    @Override
    public void validateRejection(final AgentSettlement settlement, final Long currentUserId, final String rejectionReason) {
        validateSettlementExists(settlement);
        if (!settlement.isSubmitted()) {
            throw new AgentConfigurationException("settlement.status.invalid.for.rejection", "Only submitted settlements can be rejected.",
                    settlement.getId(), settlement.getStatus());
        }
        validateNotMaker(settlement, currentUserId, "reject");
        if (StringUtils.isBlank(rejectionReason)) {
            throw new AgentConfigurationException("settlement.rejection.reason.required",
                    "A rejection reason is required to reject a settlement.", settlement.getId());
        }
    }

    @Override
    public void validateCancellation(final AgentSettlement settlement, final Long currentUserId) {
        validateSettlementExists(settlement);
        if (!settlement.isDraft()) {
            throw new AgentConfigurationException("settlement.status.invalid.for.cancellation", "Only draft settlements can be cancelled.",
                    settlement.getId(), settlement.getStatus());
        }
        validateMaker(settlement, currentUserId);
    }

    private void validateTransactionSelection(final List<Long> requestedTransactionIds,
            final List<AgentCollectionTransaction> transactions) {
        if (requestedTransactionIds == null || requestedTransactionIds.isEmpty()) {
            throw new AgentConfigurationException("settlement.transactions.empty", "A settlement must contain at least one collection.");
        }
        final Set<Long> distinctIds = new HashSet<>(requestedTransactionIds);
        if (distinctIds.size() != requestedTransactionIds.size()) {
            throw new AgentConfigurationException("settlement.transactions.duplicate",
                    "A settlement request cannot contain duplicate collection transaction ids.");
        }
        if (transactions == null || transactions.size() != requestedTransactionIds.size()) {
            throw new AgentConfigurationException("settlement.transactions.not.found",
                    "One or more selected agent collection transactions were not found.");
        }
    }

    private void validateSelectedTransactionsForDraft(final Agent agent, final List<AgentCollectionTransaction> transactions) {
        for (final AgentCollectionTransaction transaction : transactions) {
            validateTransactionBelongsToAgent(agent, transaction);
            this.agentValidationService.validateAgentAccessToOffice(agent, transaction.getOfficeId());
            this.agentValidationService.validateCurrency(agent, transaction.getCurrencyCode());
            if (transaction.isLinkedToSettlement()) {
                throw new AgentConfigurationException("settlement.transaction.already.linked",
                        "The selected agent collection transaction is already linked to another settlement.", transaction.getId(),
                        transaction.getSettlementId());
            }
            validateTransactionIsPendingForSettlement(transaction);
        }
    }

    private void validateTransactionSelectionForExistingSettlement(final AgentSettlement settlement,
            final List<AgentCollectionTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            throw new AgentConfigurationException("settlement.transactions.empty", "A settlement must contain at least one collection.",
                    settlement.getId());
        }
        for (final AgentCollectionTransaction transaction : transactions) {
            validateTransactionBelongsToAgent(settlement.getAgent(), transaction);
            if (!Objects.equals(transaction.getSettlementId(), settlement.getId())) {
                throw new AgentConfigurationException("settlement.transaction.link.mismatch",
                        "The selected agent collection transaction is not linked to this settlement.", transaction.getId(),
                        settlement.getId(), transaction.getSettlementId());
            }
            validateTransactionIsPendingForSettlement(transaction);
        }
    }

    private void validateTransactionBelongsToAgent(final Agent agent, final AgentCollectionTransaction transaction) {
        if (!Objects.equals(transaction.getAgentId(), agent.getId())) {
            throw new AgentConfigurationException("settlement.transaction.agent.mismatch",
                    "All selected transactions must belong to the settlement agent.", transaction.getId(), transaction.getAgentId(),
                    agent.getId());
        }
    }

    private void validateTransactionIsPendingForSettlement(final AgentCollectionTransaction transaction) {
        if (transaction.isSettled()) {
            throw new AgentConfigurationException("settlement.transaction.already.settled",
                    "The selected agent collection transaction has already been settled.", transaction.getId());
        }
        if (transaction.isReversed()) {
            throw new AgentConfigurationException("settlement.transaction.reversed",
                    "Reversed agent collection transactions cannot be settled.", transaction.getId());
        }
        if (!transaction.isPending()) {
            throw new AgentConfigurationException("settlement.transaction.status.invalid",
                    "Only pending agent collection transactions can be selected for settlement.", transaction.getId(),
                    transaction.getStatus());
        }
    }

    private void validateSubmittedAmount(final BigDecimal expectedAmount, final BigDecimal submittedAmount) {
        if (submittedAmount == null || submittedAmount.signum() <= 0) {
            throw new AgentConfigurationException("settlement.submitted.amount.invalid",
                    "Settlement submitted amount must be greater than zero.", submittedAmount);
        }
        if (expectedAmount.compareTo(submittedAmount) != 0) {
            throw new AgentConfigurationException("settlement.amount.mismatch",
                    "Settlement submitted amount must equal the selected collection amount for the MVP.", expectedAmount, submittedAmount);
        }
    }

    private BigDecimal sum(final List<AgentCollectionTransaction> transactions) {
        return transactions.stream().map(AgentCollectionTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateSettlementExists(final AgentSettlement settlement) {
        if (settlement == null) {
            throw new AgentConfigurationException("settlement.not.found", "Agent settlement was not found.");
        }
    }

    private void validateMaker(final AgentSettlement settlement, final Long currentUserId) {
        if (settlement.getAgent().getAppUser() == null || !Objects.equals(settlement.getAgent().getAppUser().getId(), currentUserId)) {
            throw new AgentConfigurationException("settlement.user.not.maker",
                    "Only the settlement agent can perform this settlement action.", settlement.getId(), currentUserId);
        }
    }

    private void validateNotMaker(final AgentSettlement settlement, final Long currentUserId, final String action) {
        if (Objects.equals(settlement.getSubmittedByUserId(), currentUserId)) {
            throw new AgentConfigurationException("settlement.maker.checker.violation",
                    "The settlement maker cannot " + action + " their own settlement request.", settlement.getId(), currentUserId);
        }
    }
}
