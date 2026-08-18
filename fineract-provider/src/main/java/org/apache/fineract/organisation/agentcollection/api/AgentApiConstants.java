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
package org.apache.fineract.organisation.agentcollection.api;

import java.util.List;
import java.util.Set;

public final class AgentApiConstants {

    private AgentApiConstants() {}

    public static final String AGENT_RESOURCE_NAME = "AGENT";
    public static final String AGENT_ENTITY_NAME = "AGENT";
    public static final String AGENT_COLLECTION_RESOURCE_NAME = "AGENT_COLLECTION";
    public static final String AGENT_CASH_BALANCE_RESOURCE_NAME = "AGENT_CASH_BALANCE";
    public static final String AGENT_SETTLEMENT_RESOURCE_NAME = "AGENT_SETTLEMENT";

    public static final String commandParamName = "command";
    public static final String activateCommandParamName = "activate";
    public static final String deactivateCommandParamName = "deactivate";

    public static final String localeParamName = "locale";
    public static final String appUserIdParamName = "appUserId";
    public static final String staffIdParamName = "staffId";
    public static final String officeIdParamName = "officeId";
    public static final String paymentTypeIdParamName = "paymentTypeId";
    public static final String currencyCodeParamName = "currencyCode";
    public static final String associationsParamName = "associations";
    public static final String activeParamName = "active";
    public static final String maximumCashInHandParamName = "maximumCashInHand";
    public static final String maximumDailyTotalCollectionParamName = "maximumDailyTotalCollection";
    public static final String transactionLimitsParamName = "transactionLimits";
    public static final String transactionTypeParamName = "transactionType";
    public static final String maximumSingleAmountParamName = "maximumSingleAmount";
    public static final String maximumDailyAmountParamName = "maximumDailyAmount";
    public static final String enabledParamName = "enabled";

    public static final String statusParamName = "status";
    public static final String offsetParamName = "offset";
    public static final String limitParamName = "limit";
    public static final String orderByParamName = "orderBy";
    public static final String sortOrderParamName = "sortOrder";
    public static final String agentIdParamName = "agentId";
    public static final String businessDateParamName = "businessDate";
    public static final String fromDateParamName = "fromDate";
    public static final String toDateParamName = "toDate";
    public static final String dateFormatParamName = "dateFormat";
    public static final String settlementIdParamName = "settlementId";
    public static final String settlementDateParamName = "settlementDate";
    public static final String transactionIdsParamName = "transactionIds";
    public static final String submittedAmountParamName = "submittedAmount";
    public static final String referenceNumberParamName = "referenceNumber";
    public static final String notesParamName = "notes";
    public static final String rejectionReasonParamName = "rejectionReason";
    public static final String submitCommandParamName = "submit";
    public static final String approveCommandParamName = "approve";
    public static final String rejectCommandParamName = "reject";
    public static final String cancelCommandParamName = "cancel";
    public static final String submitAgentSettlementPermissionName = "SUBMIT_AGENT_SETTLEMENT";
    public static final String approveAgentSettlementPermissionName = "APPROVE_AGENT_SETTLEMENT";
    public static final String rejectAgentSettlementPermissionName = "REJECT_AGENT_SETTLEMENT";
    public static final String cancelAgentSettlementPermissionName = "CANCEL_AGENT_SETTLEMENT";
    public static final String readAgentCashBalancePermissionName = "READ_AGENT_CASH_BALANCE";

    public static final String loanRepaymentTransactionType = "LOAN_REPAYMENT";
    public static final String savingsDepositTransactionType = "SAVINGS_DEPOSIT";

    public static final Set<String> AGENT_RESPONSE_DATA_PARAMETERS = Set.of("id", "appUserId", "appUserName", "staffId", "staffName",
            "officeId", "officeName", "paymentTypeId", "paymentTypeName", "currencyCode", "status", "maximumCashInHand",
            "maximumDailyTotalCollection", "loanCollectionLimit", "savingsCollectionLimit", "transactionLimits");

    public static final Set<String> AGENT_TEMPLATE_RESPONSE_DATA_PARAMETERS = Set.of("offices", "staff", "users", "currencies",
            "paymentTypes", "agents", "id", "name");

    public static final Set<String> AGENT_COLLECTION_SUMMARY_RESPONSE_DATA_PARAMETERS = Set.of("agent", "businessDate", "currentCashInHand",
            "todaysLoanCollections", "todaysSavingsCollections", "todaysTotalCollections", "pendingSettlementAmount",
            "pendingTransactionCount", "lastSettlementDate", "remainingLoanDailyLimit", "remainingSavingsDailyLimit",
            "remainingTotalDailyLimit", "remainingCashCapacity");

    public static final Set<String> AGENT_COLLECTION_TRANSACTION_RESPONSE_DATA_PARAMETERS = Set.of("id", "agentId", "officeId",
            "officeName", "transactionType", "loanId", "loanTransactionId", "savingsAccountId", "savingsTransactionId", "paymentTypeId",
            "paymentTypeName", "amount", "currencyCode", "transactionDate", "status", "settlementId", "settledDate", "externalId",
            "mobileReference", "runningBalance");

    public static final Set<String> AGENT_SETTLEMENT_RESPONSE_DATA_PARAMETERS = Set.of("id", "agentId", "officeId", "officeName",
            "settlementDate", "expectedAmount", "submittedAmount", "currencyCode", "status", "referenceNumber", "notes",
            "submittedByUserId", "submittedOnDate", "approvedByUserId", "approvedOnDate", "rejectedByUserId", "rejectedOnDate",
            "rejectionReason", "cancelledByUserId", "cancelledOnDate", "transactions");

    public static final Set<String> CREATE_REQUEST_DATA_PARAMETERS = Set.of(localeParamName, appUserIdParamName, staffIdParamName,
            officeIdParamName, paymentTypeIdParamName, currencyCodeParamName, activeParamName, maximumCashInHandParamName,
            maximumDailyTotalCollectionParamName, transactionLimitsParamName);

    public static final Set<String> UPDATE_REQUEST_DATA_PARAMETERS = Set.of(localeParamName, appUserIdParamName, staffIdParamName,
            officeIdParamName, paymentTypeIdParamName, currencyCodeParamName, maximumCashInHandParamName,
            maximumDailyTotalCollectionParamName, transactionLimitsParamName);

    public static final Set<String> TRANSACTION_LIMIT_REQUEST_DATA_PARAMETERS = Set.of(transactionTypeParamName,
            maximumSingleAmountParamName, maximumDailyAmountParamName, enabledParamName);

    public static final Set<String> CREATE_SETTLEMENT_REQUEST_DATA_PARAMETERS = Set.of(localeParamName, dateFormatParamName,
            agentIdParamName, transactionIdsParamName, settlementDateParamName, submittedAmountParamName, referenceNumberParamName,
            notesParamName);

    public static final Set<String> REJECT_SETTLEMENT_REQUEST_DATA_PARAMETERS = Set.of(rejectionReasonParamName);

    public static final List<String> SUPPORTED_TRANSACTION_TYPES = List.of(loanRepaymentTransactionType, savingsDepositTransactionType);
}
