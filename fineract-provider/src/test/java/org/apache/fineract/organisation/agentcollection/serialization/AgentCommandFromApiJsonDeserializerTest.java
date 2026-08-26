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
package org.apache.fineract.organisation.agentcollection.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.service.AgentTransactionLimitCommand;
import org.junit.jupiter.api.Test;

class AgentCommandFromApiJsonDeserializerTest {

    private final AgentCommandFromApiJsonDeserializer validator = new AgentCommandFromApiJsonDeserializer(new FromJsonHelper());

    @Test
    void validateForCreate_acceptsCompleteMvpAgentConfiguration() {
        assertDoesNotThrow(() -> validator.validateForCreate(validCreateJson()));
    }

    @Test
    void validateForCreate_requiresCurrencyCode() {
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate("""
                {
                  "appUserId": 11,
                  "staffId": 22,
                  "officeId": 1,
                  "paymentTypeId": 7,
                  "maximumCashInHand": 5000,
                  "maximumDailyTotalCollection": 10000,
                  "transactionLimits": [
                    { "transactionType": "LOAN_REPAYMENT", "maximumSingleAmount": 1000, "maximumDailyAmount": 5000, "enabled": true },
                    { "transactionType": "SAVINGS_DEPOSIT", "maximumSingleAmount": 800, "maximumDailyAmount": 4000, "enabled": true }
                  ]
                }
                """));
    }

    @Test
    void validateForCreate_requiresBothMvpTransactionTypes() {
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate("""
                {
                  "appUserId": 11,
                  "staffId": 22,
                  "officeId": 1,
                  "paymentTypeId": 7,
                  "currencyCode": "USD",
                  "maximumCashInHand": 5000,
                  "maximumDailyTotalCollection": 10000,
                  "transactionLimits": [
                    { "transactionType": "LOAN_REPAYMENT", "maximumSingleAmount": 1000, "maximumDailyAmount": 5000, "enabled": true }
                  ]
                }
                """));
    }

    @Test
    void validateForCreate_rejectsDuplicateTransactionTypes() {
        assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate("""
                {
                  "appUserId": 11,
                  "staffId": 22,
                  "officeId": 1,
                  "paymentTypeId": 7,
                  "currencyCode": "USD",
                  "maximumCashInHand": 5000,
                  "maximumDailyTotalCollection": 10000,
                  "transactionLimits": [
                    { "transactionType": "LOAN_REPAYMENT", "maximumSingleAmount": 1000, "maximumDailyAmount": 5000, "enabled": true },
                    { "transactionType": "LOAN_REPAYMENT", "maximumSingleAmount": 800, "maximumDailyAmount": 4000, "enabled": true }
                  ]
                }
                """));
    }

    @Test
    void extractTransactionLimits_mapsTransactionTypes() {
        final Map<AgentTransactionType, AgentTransactionLimitCommand> limits = validator
                .extractTransactionLimits(validator.parse(validCreateJson()));

        assertThat(limits).containsOnlyKeys(AgentTransactionType.LOAN_REPAYMENT, AgentTransactionType.SAVINGS_DEPOSIT);
        assertThat(limits.get(AgentTransactionType.LOAN_REPAYMENT).enabled()).isTrue();
    }

    private String validCreateJson() {
        return """
                {
                  "appUserId": 11,
                  "staffId": 22,
                  "officeId": 1,
                  "paymentTypeId": 7,
                  "currencyCode": "USD",
                  "maximumCashInHand": 5000,
                  "maximumDailyTotalCollection": 10000,
                  "transactionLimits": [
                    { "transactionType": "LOAN_REPAYMENT", "maximumSingleAmount": 1000, "maximumDailyAmount": 5000, "enabled": true },
                    { "transactionType": "SAVINGS_DEPOSIT", "maximumSingleAmount": 800, "maximumDailyAmount": 4000, "enabled": true }
                  ]
                }
                """;
    }
}
