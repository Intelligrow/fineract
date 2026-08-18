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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.organisation.agentcollection.data.AgentData;
import org.apache.fineract.organisation.agentcollection.data.AgentTemplateData;
import org.apache.fineract.organisation.agentcollection.data.AgentTemplateOptionData;
import org.apache.fineract.organisation.agentcollection.data.AgentTransactionLimitData;
import org.apache.fineract.organisation.agentcollection.domain.AgentStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.apache.fineract.organisation.agentcollection.exception.AgentNotFoundException;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.organisation.staff.service.StaffReadService;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
public class AgentReadPlatformServiceImpl implements AgentReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final PaginationHelper paginationHelper;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final SqlValidator sqlValidator;
    private final OfficeReadPlatformService officeReadPlatformService;
    private final StaffReadService staffReadService;
    private final CurrencyReadPlatformService currencyReadPlatformService;
    private final PaymentTypeReadService paymentTypeReadService;

    @Override
    public AgentData retrieveAgent(final Long agentId) {
        try {
            Optional<Long> loggedInAgentId = retrieveLoggedInUserAgentId();
            loggedInAgentId.ifPresent(loggedAgentId ->{
               if(!Objects.equals(agentId, loggedAgentId)){
                   throw new AgentConfigurationException("error.only.office.staff.can.access.agent.details",
                           "Only office Staff can access agent details");
               }
            });

            final AgentMapper mapper = new AgentMapper();
            final String hierarchySearchString = this.context.authenticatedUser().getOffice().getHierarchy() + "%";
            final String sql = "select " + mapper.schema() + " where a.id = ? and o.hierarchy like ?";
            final AgentData agent = this.jdbcTemplate.queryForObject(sql, mapper, agentId, hierarchySearchString);
            return withCollectionLimits(agent, retrieveTransactionLimits(agentId));
        } catch (final EmptyResultDataAccessException e) {
            throw new AgentNotFoundException(agentId, e);
        }
    }
    @Override
    public Optional<Long> retrieveLoggedInUserAgentId() {
        try {
            AppUser appUser = this.context.authenticatedUser();
            final String sql = "select id from m_agent as a where a.appuser_id = "+appUser.getId();
            return Optional.ofNullable(this.jdbcTemplate.queryForObject(sql, Long.class));
        }catch (final EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Page<AgentData> retrieveAllAgents(final SearchParameters searchParameters) {
        this.sqlValidator.validate(searchParameters.getOrderBy());
        this.sqlValidator.validate(searchParameters.getSortOrder());

        final AgentMapper mapper = new AgentMapper();
        final List<Object> params = new ArrayList<>();
        final StringBuilder sqlBuilder = new StringBuilder("select ")
                .append(mapper.schema())
                .append(" where o.hierarchy like ?");
        AppUser appUser = this.context.authenticatedUser();
        params.add(appUser.getOffice().getHierarchy() + "%");
        Optional<Long> loggedInAgentId = retrieveLoggedInUserAgentId();

        loggedInAgentId.ifPresent(agentId -> {
            sqlBuilder.append(" and a.id= ?");
            params.add(agentId);
        });

        if (searchParameters.hasOfficeId()) {
            sqlBuilder.append(" and a.office_id = ?");
            params.add(searchParameters.getOfficeId());
        }

        final Integer statusEnum = parseStatus(searchParameters.getStatus());
        if (statusEnum != null) {
            sqlBuilder.append(" and a.status_enum = ?");
            params.add(statusEnum);
        }

        sqlBuilder.append(" order by ").append(resolveOrderBy(searchParameters.getOrderBy())).append(" ")
                .append(resolveSortOrder(searchParameters.getSortOrder()));

        if (searchParameters.hasLimit()) {
            sqlBuilder.append(" ");
            if (searchParameters.hasOffset()) {
                sqlBuilder.append(this.sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlBuilder.append(this.sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        final Page<AgentData> page = this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), params.toArray(), mapper);
        final List<AgentData> agentsWithLimits = page.getPageItems().stream()
                .map(agent -> withCollectionLimits(agent, retrieveTransactionLimits(agent.getId()))).toList();
        return new Page<>(agentsWithLimits, page.getTotalFilteredRecords());
    }

    @Override
    public AgentTemplateData retrieveTemplate(final String associations) {
        final Set<AgentTemplateAssociation> requestedAssociations = parseTemplateAssociations(associations);
        final AgentTemplateData.AgentTemplateDataBuilder builder = AgentTemplateData.builder();

        if (requestedAssociations.contains(AgentTemplateAssociation.OFFICES)) {
            builder.offices(officeOptions());
        }
        if (requestedAssociations.contains(AgentTemplateAssociation.STAFF)) {
            builder.staff(staffOptions());
        }
        if (requestedAssociations.contains(AgentTemplateAssociation.USERS)) {
            builder.users(userOptions());
        }
        if (requestedAssociations.contains(AgentTemplateAssociation.CURRENCIES)) {
            builder.currencies(currencyOptions());
        }
        if (requestedAssociations.contains(AgentTemplateAssociation.PAYMENT_TYPES)) {
            builder.paymentTypes(paymentTypeOptions());
        }
        if (requestedAssociations.contains(AgentTemplateAssociation.AGENTS)) {
            builder.agents(agentOptions());
        }

        return builder.build();
    }

    private Set<AgentTemplateAssociation> parseTemplateAssociations(final String associations) {
        if (StringUtils.isBlank(associations) || "all".equalsIgnoreCase(associations.trim())) {
            return EnumSet.allOf(AgentTemplateAssociation.class);
        }

        final Set<AgentTemplateAssociation> requestedAssociations = Arrays.stream(associations.split(",")).map(String::trim)
                .filter(StringUtils::isNotBlank).map(this::parseTemplateAssociation)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AgentTemplateAssociation.class)));
        if (requestedAssociations.isEmpty()) {
            throw new UnrecognizedQueryParamException("associations", associations, supportedTemplateAssociations());
        }
        return requestedAssociations;
    }

    private AgentTemplateAssociation parseTemplateAssociation(final String association) {
        return switch (association.toLowerCase(Locale.ROOT)) {
            case "office", "offices" -> AgentTemplateAssociation.OFFICES;
            case "staff" -> AgentTemplateAssociation.STAFF;
            case "user", "users" -> AgentTemplateAssociation.USERS;
            case "currency", "currencies" -> AgentTemplateAssociation.CURRENCIES;
            case "paymenttype", "paymenttypes", "payment_type", "payment_types" -> AgentTemplateAssociation.PAYMENT_TYPES;
            case "agent", "agents" -> AgentTemplateAssociation.AGENTS;
            default -> throw new UnrecognizedQueryParamException("associations", association, supportedTemplateAssociations());
        };
    }

    private Object[] supportedTemplateAssociations() {
        return new Object[] { "all", "offices", "staff", "users", "currencies", "paymentTypes", "agents" };
    }

    private Collection<AgentTemplateOptionData> officeOptions() {
        return this.officeReadPlatformService.retrieveAllOfficesForDropdown().stream()
                .map(office -> option(office.getId(), StringUtils.defaultIfBlank(office.getNameDecorated(), office.getName()))).toList();
    }

    private Collection<AgentTemplateOptionData> staffOptions() {
        return this.staffReadService.retrieveAllStaff(null, false, "active").stream()
                .map(staff -> option(staff.getId(), staff.getDisplayName())).toList();
    }

    private Collection<AgentTemplateOptionData> userOptions() {
        final String hierarchySearchString = this.context.authenticatedUser().getOffice().getHierarchy() + "%";
        final String sql = "select u.id as id, u.username as name from m_appuser u "
                + "join m_office o on o.id = u.office_id where o.hierarchy like ? and u.is_deleted = false order by u.username";
        return this.jdbcTemplate.query(sql, new AgentTemplateOptionMapper(), hierarchySearchString);
    }

    private Collection<AgentTemplateOptionData> currencyOptions() {
        return this.currencyReadPlatformService.retrieveAllowedCurrencies().stream()
                .map(currency -> option(currency.getCode(), StringUtils.defaultIfBlank(currency.getName(), currency.getCode()))).toList();
    }

    private Collection<AgentTemplateOptionData> paymentTypeOptions() {
        return this.paymentTypeReadService.retrieveAllPaymentTypes().stream().map(
                paymentType -> option(paymentType.getId(), StringUtils.defaultIfBlank(paymentType.getName(), paymentType.getCodeName())))
                .toList();
    }

    private Collection<AgentTemplateOptionData> agentOptions() {
        final String hierarchySearchString = this.context.authenticatedUser().getOffice().getHierarchy() + "%";
        final String sql = "select a.id as id, coalesce(s.display_name, au.username) as name from m_agent a "
                + "join m_appuser au on au.id = a.appuser_id join m_staff s on s.id = a.staff_id "
                + "join m_office o on o.id = a.office_id where o.hierarchy like ? order by name";
        return this.jdbcTemplate.query(sql, new AgentTemplateOptionMapper(), hierarchySearchString);
    }

    private AgentTemplateOptionData option(final Object id, final String name) {
        return AgentTemplateOptionData.builder().id(id).name(name).build();
    }

    private AgentData withCollectionLimits(final AgentData agent, final List<AgentTransactionLimitData> transactionLimits) {
        return agent.toBuilder().loanCollectionLimit(findCollectionLimit(transactionLimits, AgentTransactionType.LOAN_REPAYMENT))
                .savingsCollectionLimit(findCollectionLimit(transactionLimits, AgentTransactionType.SAVINGS_DEPOSIT))
                .transactionLimits(transactionLimits).build();
    }

    private AgentTransactionLimitData findCollectionLimit(final List<AgentTransactionLimitData> transactionLimits,
            final AgentTransactionType transactionType) {
        return transactionLimits.stream()
                .filter(limit -> limit.getTransactionType() != null
                        && transactionType.getValue().longValue() == limit.getTransactionType().getId().longValue())
                .findFirst().orElse(null);
    }

    private List<AgentTransactionLimitData> retrieveTransactionLimits(final Long agentId) {
        final AgentTransactionLimitMapper mapper = new AgentTransactionLimitMapper();
        final String sql = "select " + mapper.schema() + " where atl.agent_id = ? order by atl.transaction_type_enum";
        return this.jdbcTemplate.query(sql, mapper, agentId);
    }

    private Integer parseStatus(final String status) {
        if (StringUtils.isBlank(status) || "all".equalsIgnoreCase(status)) {
            return null;
        }
        if ("active".equalsIgnoreCase(status)) {
            return AgentStatusType.ACTIVE.getValue();
        }
        if ("inactive".equalsIgnoreCase(status)) {
            return AgentStatusType.INACTIVE.getValue();
        }
        throw new UnrecognizedQueryParamException("status", status, new Object[] { "active", "inactive", "all" });
    }

    private String resolveOrderBy(final String orderBy) {
        if (StringUtils.isBlank(orderBy)) {
            return "a.id";
        }
        return switch (orderBy) {
            case "id" -> "a.id";
            case "appUserName" -> "au.username";
            case "staffName" -> "s.display_name";
            case "officeName" -> "o.name";
            case "status" -> "a.status_enum";
            case "currencyCode" -> "a.currency_code";
            default -> throw new UnrecognizedQueryParamException("orderBy", orderBy,
                    new Object[] { "id", "appUserName", "staffName", "officeName", "status", "currencyCode" });
        };
    }

    private String resolveSortOrder(final String sortOrder) {
        if (StringUtils.isBlank(sortOrder) || "ASC".equalsIgnoreCase(sortOrder)) {
            return "ASC";
        }
        if ("DESC".equalsIgnoreCase(sortOrder)) {
            return "DESC";
        }
        throw new UnrecognizedQueryParamException("sortOrder", sortOrder, new Object[] { "ASC", "DESC" });
    }

    private static final class AgentMapper implements RowMapper<AgentData> {

        public String schema() {
            return "a.id as id, a.appuser_id as appUserId, au.username as appUserName, a.staff_id as staffId, "
                    + "s.display_name as staffName, a.office_id as officeId, o.name as officeName, "
                    + "a.payment_type_id as paymentTypeId, pt.value as paymentTypeName, a.currency_code as currencyCode, "
                    + "a.status_enum as statusEnum, a.maximum_cash_in_hand as maximumCashInHand, "
                    + "a.maximum_daily_total_collection as maximumDailyTotalCollection "
                    + "from m_agent a "
                    + "join m_appuser au on au.id = a.appuser_id "
                    + "join m_staff s on s.id = a.staff_id "
                    + "join m_office o on o.id = a.office_id "
                    + "join m_payment_type pt on pt.id = a.payment_type_id";
        }

        @Override
        public AgentData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Long appUserId = rs.getLong("appUserId");
            final String appUserName = rs.getString("appUserName");
            final Long staffId = rs.getLong("staffId");
            final String staffName = rs.getString("staffName");
            final Long officeId = rs.getLong("officeId");
            final String officeName = rs.getString("officeName");
            final Long paymentTypeId = rs.getLong("paymentTypeId");
            final String paymentTypeName = rs.getString("paymentTypeName");
            final String currencyCode = rs.getString("currencyCode");
            final Integer statusEnum = JdbcSupport.getInteger(rs, "statusEnum");
            final BigDecimal maximumCashInHand = rs.getBigDecimal("maximumCashInHand");
            final BigDecimal maximumDailyTotalCollection = rs.getBigDecimal("maximumDailyTotalCollection");

            return AgentData.builder().id(id).appUserId(appUserId).appUserName(appUserName).staffId(staffId).staffName(staffName)
                    .officeId(officeId).officeName(officeName).paymentTypeId(paymentTypeId).paymentTypeName(paymentTypeName)
                    .currencyCode(currencyCode).status(AgentEnumerations.agentStatus(statusEnum)).maximumCashInHand(maximumCashInHand)
                    .maximumDailyTotalCollection(maximumDailyTotalCollection).build();
        }
    }

    private static final class AgentTransactionLimitMapper implements RowMapper<AgentTransactionLimitData> {

        public String schema() {
            return "atl.id as id, atl.transaction_type_enum as transactionTypeEnum, "
                    + "atl.maximum_single_amount as maximumSingleAmount, atl.maximum_daily_amount as maximumDailyAmount, "
                    + "atl.is_enabled as enabled from m_agent_transaction_limit atl";
        }

        @Override
        public AgentTransactionLimitData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final Integer transactionTypeEnum = JdbcSupport.getInteger(rs, "transactionTypeEnum");
            final BigDecimal maximumSingleAmount = rs.getBigDecimal("maximumSingleAmount");
            final BigDecimal maximumDailyAmount = rs.getBigDecimal("maximumDailyAmount");
            final boolean enabled = rs.getBoolean("enabled");
            return AgentTransactionLimitData.builder().id(id).transactionType(AgentEnumerations.transactionType(transactionTypeEnum))
                    .maximumSingleAmount(maximumSingleAmount).maximumDailyAmount(maximumDailyAmount).enabled(enabled).build();
        }
    }

    private static final class AgentTemplateOptionMapper implements RowMapper<AgentTemplateOptionData> {

        @Override
        public AgentTemplateOptionData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return AgentTemplateOptionData.builder().id(rs.getObject("id")).name(rs.getString("name")).build();
        }
    }

    private enum AgentTemplateAssociation {

        OFFICES, STAFF, USERS, CURRENCIES, PAYMENT_TYPES, AGENTS
    }
}
