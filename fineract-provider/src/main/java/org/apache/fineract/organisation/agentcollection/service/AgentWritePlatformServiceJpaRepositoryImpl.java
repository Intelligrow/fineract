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

import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.activeParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.appUserIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.currencyCodeParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.maximumCashInHandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.maximumDailyTotalCollectionParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.officeIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.paymentTypeIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.staffIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.transactionLimitsParamName;

import com.google.gson.JsonElement;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentRepositoryWrapper;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionLimit;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.organisation.agentcollection.serialization.AgentCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepository;
import org.apache.fineract.portfolio.paymenttype.exception.PaymentTypeNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.exception.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class AgentWritePlatformServiceJpaRepositoryImpl implements AgentWritePlatformService {

    private final PlatformSecurityContext context;
    private final AgentCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final AgentRepositoryWrapper agentRepositoryWrapper;
    private final AgentValidationService agentValidationService;
    private final AppUserRepository appUserRepository;
    private final StaffRepositoryWrapper staffRepositoryWrapper;
    private final PaymentTypeRepository paymentTypeRepository;
    private final CurrencyReadPlatformService currencyReadPlatformService;

    @Transactional
    @Override
    public CommandProcessingResult createAgent(final JsonCommand command) {
        try {
            final AppUser currentUser = this.context.authenticatedUser();
            this.fromApiJsonDeserializer.validateForCreate(command.json());

            final AppUser appUser = findValidAppUser(command.longValueOfParameterNamed(appUserIdParamName));
            final Staff staff = findValidStaff(command.longValueOfParameterNamed(staffIdParamName));
            final Office office = this.agentValidationService.validateUserPrivilegeOnOfficeAndRetrieve(currentUser,
                    command.longValueOfParameterNamed(officeIdParamName));
            final PaymentType paymentType = findPaymentType(command.longValueOfParameterNamed(paymentTypeIdParamName));
            final String currencyCode = validateAndNormalizeCurrencyCode(command.stringValueOfParameterNamed(currencyCodeParamName));

            this.agentValidationService.validateOfficeRelationships(office, appUser, staff);
            validateDuplicateAppUserMapping(appUser.getId(), null);
            validateDuplicateStaffMapping(staff.getId(), null);

            final boolean active = !command.parameterExists(activeParamName)
                    || command.booleanPrimitiveValueOfParameterNamed(activeParamName);
            final Agent agent = Agent.createNew(appUser, staff, office, paymentType, currencyCode,
                    command.bigDecimalValueOfParameterNamed(maximumCashInHandParamName),
                    command.bigDecimalValueOfParameterNamed(maximumDailyTotalCollectionParamName), active);

            final Map<AgentTransactionType, AgentTransactionLimitCommand> limitCommands = this.fromApiJsonDeserializer
                    .extractTransactionLimits(command.parsedJson());
            limitCommands.values().forEach(limitCommand -> agent.addTransactionLimit(limitCommand.transactionType(),
                    limitCommand.maximumSingleAmount(), limitCommand.maximumDailyAmount(), Boolean.TRUE.equals(limitCommand.enabled())));

            this.agentRepositoryWrapper.saveAndFlush(agent);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(agent.getId())
                    .withOfficeId(office.getId()).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException dve) {
            final Throwable realCause = ExceptionUtils.getRootCause(dve);
            handleDataIntegrityIssues(command, realCause, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult updateAgent(final JsonCommand command) {
        try {
            final AppUser currentUser = this.context.authenticatedUser();
            this.fromApiJsonDeserializer.validateForUpdate(command.json());

            final Agent agent = this.agentValidationService.findAgentLocked(command.entityId());
            this.agentValidationService.validateUserPrivilegeOnOfficeAndRetrieve(currentUser, agent.getOffice().getId());

            AppUser appUser = agent.getAppUser();
            Staff staff = agent.getStaff();
            Office office = agent.getOffice();
            PaymentType paymentType = agent.getPaymentType();
            String currencyCode = agent.getCurrencyCode();

            final Map<String, Object> changes = new LinkedHashMap<>();

            if (command.parameterExists(appUserIdParamName)) {
                final Long appUserId = command.longValueOfParameterNamed(appUserIdParamName);
                validateDuplicateAppUserMapping(appUserId, agent.getId());
                appUser = findValidAppUser(appUserId);
                if (!agent.getAppUser().getId().equals(appUser.getId())) {
                    agent.updateAppUser(appUser);
                    changes.put(appUserIdParamName, appUserId);
                }
            }

            if (command.parameterExists(staffIdParamName)) {
                final Long staffId = command.longValueOfParameterNamed(staffIdParamName);
                validateDuplicateStaffMapping(staffId, agent.getId());
                staff = findValidStaff(staffId);
                if (!agent.getStaff().getId().equals(staff.getId())) {
                    agent.updateStaff(staff);
                    changes.put(staffIdParamName, staffId);
                }
            }

            if (command.parameterExists(officeIdParamName)) {
                office = this.agentValidationService.validateUserPrivilegeOnOfficeAndRetrieve(currentUser,
                        command.longValueOfParameterNamed(officeIdParamName));
                if (!agent.getOffice().getId().equals(office.getId())) {
                    agent.updateOffice(office);
                    changes.put(officeIdParamName, office.getId());
                }
            }

            this.agentValidationService.validateOfficeRelationships(office, appUser, staff);

            if (command.parameterExists(paymentTypeIdParamName)) {
                paymentType = findPaymentType(command.longValueOfParameterNamed(paymentTypeIdParamName));
                if (!agent.getPaymentType().getId().equals(paymentType.getId())) {
                    agent.updatePaymentType(paymentType);
                    changes.put(paymentTypeIdParamName, paymentType.getId());
                }
            }

            if (command.parameterExists(currencyCodeParamName)) {
                currencyCode = validateAndNormalizeCurrencyCode(command.stringValueOfParameterNamed(currencyCodeParamName));
                if (!Objects.equals(agent.getCurrencyCode(), currencyCode)) {
                    agent.updateCurrencyCode(currencyCode);
                    changes.put(currencyCodeParamName, currencyCode);
                }
            }

            if (command.isChangeInBigDecimalParameterNamed(maximumCashInHandParamName, agent.getMaximumCashInHand())) {
                final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(maximumCashInHandParamName);
                agent.updateMaximumCashInHand(newValue);
                changes.put(maximumCashInHandParamName, newValue);
            }

            if (command.isChangeInBigDecimalParameterNamed(maximumDailyTotalCollectionParamName, agent.getMaximumDailyTotalCollection())) {
                final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(maximumDailyTotalCollectionParamName);
                agent.updateMaximumDailyTotalCollection(newValue);
                changes.put(maximumDailyTotalCollectionParamName, newValue);
            }

            if (command.parameterExists(transactionLimitsParamName) && applyTransactionLimitChanges(agent, command.parsedJson())) {
                changes.put(transactionLimitsParamName, true);
            }

            if (!changes.isEmpty()) {
                this.agentRepositoryWrapper.saveAndFlush(agent);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(agent.getId())
                    .withOfficeId(agent.getOffice().getId()).with(changes).build();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException dve) {
            final Throwable realCause = ExceptionUtils.getRootCause(dve);
            handleDataIntegrityIssues(command, realCause, dve);
            return CommandProcessingResult.empty();
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult activateAgent(final JsonCommand command) {
        return changeStatus(command, true);
    }

    @Transactional
    @Override
    public CommandProcessingResult deactivateAgent(final JsonCommand command) {
        return changeStatus(command, false);
    }

    private CommandProcessingResult changeStatus(final JsonCommand command, final boolean active) {
        final AppUser currentUser = this.context.authenticatedUser();
        final Agent agent = this.agentValidationService.findAgentLocked(command.entityId());
        this.agentValidationService.validateUserPrivilegeOnOfficeAndRetrieve(currentUser, agent.getOffice().getId());

        final Map<String, Object> changes = new LinkedHashMap<>();
        final boolean changed = active ? agent.activate() : agent.deactivate();
        if (changed) {
            changes.put(activeParamName, active);
            this.agentRepositoryWrapper.saveAndFlush(agent);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(agent.getId())
                .withOfficeId(agent.getOffice().getId()).with(changes).build();
    }

    private boolean applyTransactionLimitChanges(final Agent agent, final JsonElement jsonElement) {
        boolean changed = false;
        final Map<AgentTransactionType, AgentTransactionLimitCommand> limitCommands = this.fromApiJsonDeserializer
                .extractTransactionLimits(jsonElement);
        for (AgentTransactionLimitCommand limitCommand : limitCommands.values()) {
            final AgentTransactionLimit limit;
            final var existingLimit = agent.findTransactionLimit(limitCommand.transactionType());
            if (existingLimit.isPresent()) {
                limit = existingLimit.get();
            } else {
                limit = agent.addTransactionLimit(limitCommand.transactionType(), limitCommand.maximumSingleAmount(),
                        limitCommand.maximumDailyAmount(), Boolean.TRUE.equals(limitCommand.enabled()));
                changed = true;
            }
            if (!sameMoney(limit.getMaximumSingleAmount(), limitCommand.maximumSingleAmount())) {
                limit.updateMaximumSingleAmount(limitCommand.maximumSingleAmount());
                changed = true;
            }
            if (!sameMoney(limit.getMaximumDailyAmount(), limitCommand.maximumDailyAmount())) {
                limit.updateMaximumDailyAmount(limitCommand.maximumDailyAmount());
                changed = true;
            }
            if (limit.isEnabled() != Boolean.TRUE.equals(limitCommand.enabled())) {
                limit.updateEnabled(Boolean.TRUE.equals(limitCommand.enabled()));
                changed = true;
            }
        }
        return changed;
    }

    private boolean sameMoney(final BigDecimal left, final BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private AppUser findValidAppUser(final Long appUserId) {
        final AppUser appUser = this.appUserRepository.findById(appUserId).orElseThrow(() -> new UserNotFoundException(appUserId));
        if (appUser.isDeleted() || !appUser.isEnabled()) {
            throw new AgentConfigurationException("app.user.inactive.or.deleted",
                    "App user " + appUserId + " must be enabled and not deleted to be configured as an agent.", appUserId);
        }
        return appUser;
    }

    private Staff findValidStaff(final Long staffId) {
        final Staff staff = this.staffRepositoryWrapper.findOneWithNotFoundDetection(staffId);
        if (!staff.isActive()) {
            throw new AgentConfigurationException("staff.inactive",
                    "Staff member " + staffId + " must be active to be configured as an agent.", staffId);
        }
        return staff;
    }

    private PaymentType findPaymentType(final Long paymentTypeId) {
        return this.paymentTypeRepository.findById(paymentTypeId).orElseThrow(() -> new PaymentTypeNotFoundException(paymentTypeId));
    }

    private String validateAndNormalizeCurrencyCode(final String currencyCode) {
        final String normalizedCurrencyCode = StringUtils.upperCase(StringUtils.trim(currencyCode));
        final boolean allowed = this.currencyReadPlatformService.retrieveAllowedCurrencies().stream().map(CurrencyData::getCode)
                .anyMatch(code -> code != null && code.equalsIgnoreCase(normalizedCurrencyCode));
        if (!allowed) {
            throw new AgentConfigurationException("currency.not.allowed",
                    "Currency code " + normalizedCurrencyCode + " is not enabled for the organisation.", normalizedCurrencyCode);
        }
        return normalizedCurrencyCode;
    }

    private void validateDuplicateAppUserMapping(final Long appUserId, final Long existingAgentId) {
        final boolean duplicate = existingAgentId == null ? this.agentRepositoryWrapper.existsByAppUserId(appUserId)
                : this.agentRepositoryWrapper.existsByAppUserIdAndIdNot(appUserId, existingAgentId);
        if (duplicate) {
            throw new AgentConfigurationException("duplicate.app.user.mapping",
                    "An agent configuration already exists for app user " + appUserId + ".", appUserId);
        }
    }

    private void validateDuplicateStaffMapping(final Long staffId, final Long existingAgentId) {
        final boolean duplicate = existingAgentId == null ? this.agentRepositoryWrapper.existsByStaffId(staffId)
                : this.agentRepositoryWrapper.existsByStaffIdAndIdNot(staffId, existingAgentId);
        if (duplicate) {
            throw new AgentConfigurationException("duplicate.staff.mapping",
                    "An agent configuration already exists for staff member " + staffId + ".", staffId);
        }
    }

    private void handleDataIntegrityIssues(final JsonCommand command, final Throwable realCause, final Exception dve) {
        final String message = realCause == null ? "" : realCause.getMessage();
        if (message.contains("uk_m_agent_appuser")) {
            final Long appUserId = command.longValueOfParameterNamed(appUserIdParamName);
            throw new PlatformDataIntegrityException("error.msg.agent.duplicate.app.user.mapping",
                    "An agent configuration already exists for app user `" + appUserId + "`.", appUserIdParamName, appUserId);
        }
        if (message.contains("uk_m_agent_staff")) {
            final Long staffId = command.longValueOfParameterNamed(staffIdParamName);
            throw new PlatformDataIntegrityException("error.msg.agent.duplicate.staff.mapping",
                    "An agent configuration already exists for staff member `" + staffId + "`.", staffIdParamName, staffId);
        }
        if (message.contains("uk_m_agent_transaction_limit_type")) {
            throw new PlatformDataIntegrityException("error.msg.agent.transaction.limit.duplicate.type",
                    "Duplicate agent transaction limit type.", transactionLimitsParamName);
        }

        log.error("Error occurred.", dve);
        throw ErrorHandler.getMappable(dve, "error.msg.agent.unknown.data.integrity.issue",
                "Unknown data integrity issue with resource: " + message);
    }
}
