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

import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_SETTLEMENT_RESOURCE_NAME;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.CREATE_SETTLEMENT_REQUEST_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.REJECT_SETTLEMENT_REQUEST_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.agentIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.notesParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.referenceNumberParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.rejectionReasonParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.settlementDateParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.submittedAmountParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.transactionIdsParamName;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.agentcollection.data.AgentSettlementCommand;
import org.springframework.stereotype.Component;

@Component
public class AgentSettlementCommandFromApiJsonDeserializer {

    private final FromJsonHelper fromApiJsonHelper;

    public AgentSettlementCommandFromApiJsonDeserializer(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public AgentSettlementCommand validateAndParseCreate(final String json) {
        final JsonElement element = validateRequest(json, CREATE_SETTLEMENT_REQUEST_DATA_PARAMETERS);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(dataValidationErrors).resource(AGENT_SETTLEMENT_RESOURCE_NAME);

        final Long agentId = this.fromApiJsonHelper.extractLongNamed(agentIdParamName, element);
        validator.reset().parameter(agentIdParamName).value(agentId).notNull().longGreaterThanZero();

        final JsonArray transactionIds = this.fromApiJsonHelper.extractJsonArrayNamed(transactionIdsParamName, element);
        validator.reset().parameter(transactionIdsParamName).value(transactionIds).jsonArrayNotEmpty();
        final List<Long> parsedTransactionIds = extractTransactionIds(transactionIds, validator);

        final BigDecimal submittedAmount = this.fromApiJsonHelper.extractBigDecimalNamed(submittedAmountParamName, element, Locale.ENGLISH);
        validator.reset().parameter(submittedAmountParamName).value(submittedAmount).notNull().positiveAmount();

        final LocalDate settlementDate = this.fromApiJsonHelper.extractLocalDateNamed(settlementDateParamName, element);

        final String referenceNumber = this.fromApiJsonHelper.extractStringNamed(referenceNumberParamName, element);
        validator.reset().parameter(referenceNumberParamName).value(referenceNumber).ignoreIfNull().notExceedingLengthOf(100);

        final String notes = this.fromApiJsonHelper.extractStringNamed(notesParamName, element);
        validator.reset().parameter(notesParamName).value(notes).ignoreIfNull().notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
        return AgentSettlementCommand.builder().agentId(agentId).transactionIds(parsedTransactionIds).settlementDate(settlementDate)
                .submittedAmount(submittedAmount).referenceNumber(referenceNumber).notes(notes).build();
    }

    public AgentSettlementCommand validateAndParseReject(final String json) {
        final JsonElement element = validateRequest(json, REJECT_SETTLEMENT_REQUEST_DATA_PARAMETERS);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(dataValidationErrors).resource(AGENT_SETTLEMENT_RESOURCE_NAME);

        final String rejectionReason = this.fromApiJsonHelper.extractStringNamed(rejectionReasonParamName, element);
        validator.reset().parameter(rejectionReasonParamName).value(rejectionReason).notBlank().notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
        return AgentSettlementCommand.builder().rejectionReason(rejectionReason).build();
    }

    private JsonElement validateRequest(final String json, final Set<String> supportedParameters) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParameters);
        return this.fromApiJsonHelper.parse(json);
    }

    private List<Long> extractTransactionIds(final JsonArray transactionIds, final DataValidatorBuilder validator) {
        final List<Long> parsedTransactionIds = new ArrayList<>();
        if (transactionIds == null) {
            return parsedTransactionIds;
        }
        for (int i = 0; i < transactionIds.size(); i++) {
            Long transactionId = null;
            final JsonElement transactionIdElement = transactionIds.get(i);
            if (transactionIdElement != null && !transactionIdElement.isJsonNull() && transactionIdElement.isJsonPrimitive()) {
                transactionId = transactionIdElement.getAsLong();
            }
            validator.reset().parameter(transactionIdsParamName).parameterAtIndexArray(transactionIdsParamName, i + 1).value(transactionId)
                    .notNull().longGreaterThanZero();
            parsedTransactionIds.add(transactionId);
        }
        return parsedTransactionIds;
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
