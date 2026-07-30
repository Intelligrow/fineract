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

    public static final String commandParamName = "command";
    public static final String activateCommandParamName = "activate";
    public static final String deactivateCommandParamName = "deactivate";

    public static final String localeParamName = "locale";
    public static final String appUserIdParamName = "appUserId";
    public static final String staffIdParamName = "staffId";
    public static final String officeIdParamName = "officeId";
    public static final String paymentTypeIdParamName = "paymentTypeId";
    public static final String currencyCodeParamName = "currencyCode";
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

    public static final String loanRepaymentTransactionType = "LOAN_REPAYMENT";
    public static final String savingsDepositTransactionType = "SAVINGS_DEPOSIT";

    public static final Set<String> AGENT_RESPONSE_DATA_PARAMETERS = Set.of("id", "appUserId", "appUserName", "staffId", "staffName",
            "officeId", "officeName", "paymentTypeId", "paymentTypeName", "currencyCode", "status", "maximumCashInHand",
            "maximumDailyTotalCollection", "loanCollectionLimit", "savingsCollectionLimit", "transactionLimits");

    public static final Set<String> CREATE_REQUEST_DATA_PARAMETERS = Set.of(localeParamName, appUserIdParamName, staffIdParamName,
            officeIdParamName, paymentTypeIdParamName, currencyCodeParamName, activeParamName, maximumCashInHandParamName,
            maximumDailyTotalCollectionParamName, transactionLimitsParamName);

    public static final Set<String> UPDATE_REQUEST_DATA_PARAMETERS = Set.of(localeParamName, appUserIdParamName, staffIdParamName,
            officeIdParamName, paymentTypeIdParamName, currencyCodeParamName, maximumCashInHandParamName,
            maximumDailyTotalCollectionParamName, transactionLimitsParamName);

    public static final Set<String> TRANSACTION_LIMIT_REQUEST_DATA_PARAMETERS = Set.of(transactionTypeParamName,
            maximumSingleAmountParamName, maximumDailyAmountParamName, enabledParamName);

    public static final List<String> SUPPORTED_TRANSACTION_TYPES = List.of(loanRepaymentTransactionType, savingsDepositTransactionType);
}
