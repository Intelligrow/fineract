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

import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.organisation.agentcollection.domain.AgentStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;

public final class AgentEnumerations {

    private AgentEnumerations() {}

    public static EnumOptionData agentStatus(final Integer id) {
        return agentStatus(AgentStatusType.fromInt(id));
    }

    public static EnumOptionData agentStatus(final AgentStatusType type) {
        return switch (type) {
            case ACTIVE -> new EnumOptionData(type.getValue().longValue(), type.getCode(), "Active");
            case INACTIVE -> new EnumOptionData(type.getValue().longValue(), type.getCode(), "Inactive");
            default -> new EnumOptionData(AgentStatusType.INVALID.getValue().longValue(), AgentStatusType.INVALID.getCode(), "Invalid");
        };
    }

    public static EnumOptionData transactionType(final Integer id) {
        return transactionType(AgentTransactionType.fromInt(id));
    }

    public static EnumOptionData transactionType(final AgentTransactionType type) {
        return switch (type) {
            case LOAN_REPAYMENT -> new EnumOptionData(type.getValue().longValue(), type.getCode(), "Loan repayment");
            case SAVINGS_DEPOSIT -> new EnumOptionData(type.getValue().longValue(), type.getCode(), "Savings deposit");
            default ->
                new EnumOptionData(AgentTransactionType.INVALID.getValue().longValue(), AgentTransactionType.INVALID.getCode(), "Invalid");
        };
    }
}
