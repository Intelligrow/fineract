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
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;

public interface AgentCollectionWritePlatformService {

    Optional<Long> recordLoanRepaymentCollection(Long accountOfficeId, String accountCurrencyCode, Long loanId, Long loanTransactionId,
            Long paymentTypeId, BigDecimal amount, LocalDate transactionDate, String externalId, String mobileReference);

    Optional<Long> recordSavingsDepositCollection(Long accountOfficeId, String accountCurrencyCode, Long savingsAccountId,
            Long savingsTransactionId, Long paymentTypeId, BigDecimal amount, LocalDate transactionDate, String externalId,
            String mobileReference);

    Optional<Long> recordLoanRepaymentCollection(LoanTransaction deposit);

    Optional<Long> recordSavingsDepositCollection(SavingsAccountTransaction deposit);

    Optional<Long> markLoanRepaymentCollectionReversed(Long loanTransactionId);

    Optional<Long> markSavingsDepositCollectionReversed(Long savingsTransactionId);
}
