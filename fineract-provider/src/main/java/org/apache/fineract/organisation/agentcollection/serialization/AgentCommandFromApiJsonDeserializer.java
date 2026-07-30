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

import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_RESOURCE_NAME;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.CREATE_REQUEST_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.SUPPORTED_TRANSACTION_TYPES;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.TRANSACTION_LIMIT_REQUEST_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.UPDATE_REQUEST_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.activeParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.appUserIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.currencyCodeParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.enabledParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.loanRepaymentTransactionType;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.maximumCashInHandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.maximumDailyAmountParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.maximumDailyTotalCollectionParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.maximumSingleAmountParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.officeIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.paymentTypeIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.savingsDepositTransactionType;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.staffIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.transactionLimitsParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.transactionTypeParamName;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.service.AgentTransactionLimitCommand;
import org.springframework.stereotype.Component;

@Component
public class AgentCommandFromApiJsonDeserializer {

    private final FromJsonHelper fromApiJsonHelper;

    public AgentCommandFromApiJsonDeserializer(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForCreate(final String json) {
        final JsonElement element = validateRequest(json, CREATE_REQUEST_DATA_PARAMETERS);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(dataValidationErrors).resource(AGENT_RESOURCE_NAME);

        validateMandatoryLong(element, validator, appUserIdParamName);
        validateMandatoryLong(element, validator, staffIdParamName);
        validateMandatoryLong(element, validator, officeIdParamName);
        validateMandatoryLong(element, validator, paymentTypeIdParamName);
        validateCurrencyCode(element, validator, false);
        validateMandatoryPositiveAmount(element, validator, maximumCashInHandParamName);
        validateMandatoryPositiveAmount(element, validator, maximumDailyTotalCollectionParamName);
        if (this.fromApiJsonHelper.parameterExists(activeParamName, element)) {
            final Boolean active = this.fromApiJsonHelper.extractBooleanNamed(activeParamName, element);
            validator.reset().parameter(activeParamName).value(active).notNull().validateForBooleanValue();
        }
        validateTransactionLimits(element, validator, true);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateForUpdate(final String json) {
        final JsonElement element = validateRequest(json, UPDATE_REQUEST_DATA_PARAMETERS);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(dataValidationErrors).resource(AGENT_RESOURCE_NAME);

        validateOptionalLong(element, validator, appUserIdParamName);
        validateOptionalLong(element, validator, staffIdParamName);
        validateOptionalLong(element, validator, officeIdParamName);
        validateOptionalLong(element, validator, paymentTypeIdParamName);
        validateCurrencyCode(element, validator, true);
        validateOptionalPositiveAmount(element, validator, maximumCashInHandParamName);
        validateOptionalPositiveAmount(element, validator, maximumDailyTotalCollectionParamName);
        if (this.fromApiJsonHelper.parameterExists(transactionLimitsParamName, element)) {
            validateTransactionLimits(element, validator, false);
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public Map<AgentTransactionType, AgentTransactionLimitCommand> extractTransactionLimits(final JsonElement element) {
        final JsonArray jsonLimits = this.fromApiJsonHelper.extractJsonArrayNamed(transactionLimitsParamName, element);
        final Map<AgentTransactionType, AgentTransactionLimitCommand> limits = new HashMap<>();
        for (JsonElement limitElement : jsonLimits) {
            final JsonObject limitObject = limitElement.getAsJsonObject();
            final AgentTransactionType transactionType = AgentTransactionType
                    .fromString(this.fromApiJsonHelper.extractStringNamed(transactionTypeParamName, limitObject));
            final BigDecimal maximumSingleAmount = this.fromApiJsonHelper.extractBigDecimalNamed(maximumSingleAmountParamName, limitObject,
                    Locale.ENGLISH);
            final BigDecimal maximumDailyAmount = this.fromApiJsonHelper.extractBigDecimalNamed(maximumDailyAmountParamName, limitObject,
                    Locale.ENGLISH);
            final Boolean enabled = this.fromApiJsonHelper.extractBooleanNamed(enabledParamName, limitObject);
            limits.put(transactionType,
                    new AgentTransactionLimitCommand(transactionType, maximumSingleAmount, maximumDailyAmount, enabled));
        }
        return limits;
    }

    public JsonElement parse(final String json) {
        return this.fromApiJsonHelper.parse(json);
    }

    private JsonElement validateRequest(final String json, final Set<String> supportedParameters) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParameters);
        return this.fromApiJsonHelper.parse(json);
    }

    private void validateMandatoryLong(final JsonElement element, final DataValidatorBuilder validator, final String parameterName) {
        final Long value = this.fromApiJsonHelper.extractLongNamed(parameterName, element);
        validator.reset().parameter(parameterName).value(value).notNull().longGreaterThanZero();
    }

    private void validateOptionalLong(final JsonElement element, final DataValidatorBuilder validator, final String parameterName) {
        if (this.fromApiJsonHelper.parameterExists(parameterName, element)) {
            final Long value = this.fromApiJsonHelper.extractLongNamed(parameterName, element);
            validator.reset().parameter(parameterName).value(value).notNull().longGreaterThanZero();
        }
    }

    private void validateCurrencyCode(final JsonElement element, final DataValidatorBuilder validator, final boolean optional) {
        if (!optional || this.fromApiJsonHelper.parameterExists(currencyCodeParamName, element)) {
            final String currencyCode = this.fromApiJsonHelper.extractStringNamed(currencyCodeParamName, element);
            validator.reset().parameter(currencyCodeParamName).value(currencyCode).notBlank().notExceedingLengthOf(3);
        }
    }

    private void validateMandatoryPositiveAmount(final JsonElement element, final DataValidatorBuilder validator,
            final String parameterName) {
        final BigDecimal value = this.fromApiJsonHelper.extractBigDecimalNamed(parameterName, element, Locale.ENGLISH);
        validator.reset().parameter(parameterName).value(value).notNull().positiveAmount();
    }

    private void validateOptionalPositiveAmount(final JsonElement element, final DataValidatorBuilder validator,
            final String parameterName) {
        if (this.fromApiJsonHelper.parameterExists(parameterName, element)) {
            final BigDecimal value = this.fromApiJsonHelper.extractBigDecimalNamed(parameterName, element, Locale.ENGLISH);
            validator.reset().parameter(parameterName).value(value).notNull().positiveAmount();
        }
    }

    private void validateTransactionLimits(final JsonElement element, final DataValidatorBuilder validator, final boolean requireAllTypes) {
        final JsonArray limits = this.fromApiJsonHelper.extractJsonArrayNamed(transactionLimitsParamName, element);
        validator.reset().parameter(transactionLimitsParamName).value(limits).jsonArrayNotEmpty();
        if (limits == null) {
            return;
        }

        final EnumSet<AgentTransactionType> transactionTypes = EnumSet.noneOf(AgentTransactionType.class);
        for (int i = 0; i < limits.size(); i++) {
            final JsonObject limitObject = limits.get(i).getAsJsonObject();
            this.fromApiJsonHelper.checkForUnsupportedParameters(limitObject, TRANSACTION_LIMIT_REQUEST_DATA_PARAMETERS);

            final String rawTransactionType = this.fromApiJsonHelper.extractStringNamed(transactionTypeParamName, limitObject);
            validator.reset().parameter(transactionLimitsParamName).parameterAtIndexArray(transactionTypeParamName, i + 1)
                    .value(rawTransactionType).notBlank().isOneOfTheseStringValues(SUPPORTED_TRANSACTION_TYPES);

            final AgentTransactionType transactionType = AgentTransactionType.fromString(rawTransactionType);
            if (!transactionType.isInvalid() && !transactionTypes.add(transactionType)) {
                validator.reset().parameter(transactionLimitsParamName).parameterAtIndexArray(transactionTypeParamName, i + 1)
                        .value(rawTransactionType).failWithCode("transaction.type.duplicate", rawTransactionType);
            }

            final Boolean enabled = this.fromApiJsonHelper.extractBooleanNamed(enabledParamName, limitObject);
            validator.reset().parameter(transactionLimitsParamName).parameterAtIndexArray(enabledParamName, i + 1).value(enabled).notNull()
                    .validateForBooleanValue();

            validateLimitAmount(limitObject, validator, maximumSingleAmountParamName, i + 1, Boolean.TRUE.equals(enabled));
            validateLimitAmount(limitObject, validator, maximumDailyAmountParamName, i + 1, Boolean.TRUE.equals(enabled));
        }

        if (requireAllTypes) {
            requireTransactionType(transactionTypes, validator, loanRepaymentTransactionType, AgentTransactionType.LOAN_REPAYMENT);
            requireTransactionType(transactionTypes, validator, savingsDepositTransactionType, AgentTransactionType.SAVINGS_DEPOSIT);
        }
    }

    private void validateLimitAmount(final JsonObject limitObject, final DataValidatorBuilder validator, final String parameterName,
            final int index, final boolean requiredPositive) {
        final BigDecimal value = this.fromApiJsonHelper.extractBigDecimalNamed(parameterName, limitObject, Locale.ENGLISH);
        if (requiredPositive) {
            validator.reset().parameter(transactionLimitsParamName).parameterAtIndexArray(parameterName, index).value(value).notNull()
                    .positiveAmount();
        } else {
            validator.reset().parameter(transactionLimitsParamName).parameterAtIndexArray(parameterName, index).value(value).notNull()
                    .zeroOrPositiveAmount();
        }
    }

    private void requireTransactionType(final EnumSet<AgentTransactionType> transactionTypes, final DataValidatorBuilder validator,
            final String transactionTypeName, final AgentTransactionType transactionType) {
        if (!transactionTypes.contains(transactionType)) {
            validator.reset().parameter(transactionLimitsParamName).value(transactionTypeName).failWithCode("transaction.type.required",
                    transactionTypeName);
        }
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
