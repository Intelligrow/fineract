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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;

class AgentTest {

    @Test
    void activateAndDeactivateMaintainExplicitStatus() {
        final Agent agent = newAgent(false);

        assertThat(agent.status()).isEqualTo(AgentStatusType.INACTIVE);
        assertThat(agent.activate()).isTrue();
        assertThat(agent.status()).isEqualTo(AgentStatusType.ACTIVE);
        assertThat(agent.activate()).isFalse();
        assertThat(agent.deactivate()).isTrue();
        assertThat(agent.status()).isEqualTo(AgentStatusType.INACTIVE);
    }

    @Test
    void transactionLimitsAreIndexedByType() {
        final Agent agent = newAgent(true);
        agent.addTransactionLimit(AgentTransactionType.LOAN_REPAYMENT, BigDecimal.valueOf(1000), BigDecimal.valueOf(5000), true);

        assertThat(agent.findTransactionLimit(AgentTransactionType.LOAN_REPAYMENT)).isPresent();
        assertThat(agent.findTransactionLimit(AgentTransactionType.SAVINGS_DEPOSIT)).isEmpty();
    }

    private Agent newAgent(final boolean active) {
        return Agent.createNew(mock(AppUser.class), mock(Staff.class), mock(Office.class), mock(PaymentType.class), "usd",
                BigDecimal.valueOf(5000), BigDecimal.valueOf(10000), active);
    }
}
