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
package org.apache.fineract.organisation.agentcollection.domain;

import java.util.Locale;
import org.apache.commons.lang3.StringUtils;

public enum AgentTransactionType {

    INVALID(0, "agentTransactionType.invalid"), LOAN_REPAYMENT(1, "agentTransactionType.loan.repayment"), SAVINGS_DEPOSIT(2,
            "agentTransactionType.savings.deposit");

    private final Integer value;
    private final String code;

    AgentTransactionType(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public static AgentTransactionType fromInt(final Integer value) {
        if (value == null) {
            return INVALID;
        }
        return switch (value) {
            case 1 -> LOAN_REPAYMENT;
            case 2 -> SAVINGS_DEPOSIT;
            default -> INVALID;
        };
    }

    public static AgentTransactionType fromString(final String value) {
        if (StringUtils.isBlank(value)) {
            return INVALID;
        }
        try {
            return AgentTransactionType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return INVALID;
        }
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    public boolean isInvalid() {
        return this.value.equals(INVALID.value);
    }
}
