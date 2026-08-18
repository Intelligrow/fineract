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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionSearchParameters;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionSummaryData;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionTransactionData;
import org.apache.fineract.organisation.agentcollection.data.AgentData;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionStatusType;
import org.apache.fineract.organisation.agentcollection.domain.AgentTransactionType;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
public class AgentCollectionReadPlatformServiceImpl implements AgentCollectionReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final AgentReadPlatformService agentReadPlatformService;
    private final PaginationHelper paginationHelper;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final SqlValidator sqlValidator;

    @Override
    public BigDecimal retrieveCurrentCashInHand(final Long agentId) {
        final String sql = """
                select coalesce(sum(act.amount), 0)
                from m_agent_collection_transaction act
                where act.agent_id = ?
                and act.status_enum in (?, ?)
                """;
        return zeroIfNull(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, agentId,
                AgentCollectionTransactionStatusType.PENDING.getValue(), AgentCollectionTransactionStatusType.DISPUTED.getValue()));
    }

    @Override
    public BigDecimal retrieveDailyCollectionTotal(final Long agentId, final LocalDate businessDate) {
        final String sql = """
                select coalesce(sum(act.amount), 0)
                from m_agent_collection_transaction act
                where act.agent_id = ?
                and act.transaction_date = ?
                and act.status_enum <> ?
                """;
        return zeroIfNull(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, agentId, businessDate,
                AgentCollectionTransactionStatusType.REVERSED.getValue()));
    }

    @Override
    public BigDecimal retrieveDailyCollectionTotal(final Long agentId, final AgentTransactionType transactionType,
            final LocalDate businessDate) {
        final String sql = """
                select coalesce(sum(act.amount), 0)
                from m_agent_collection_transaction act
                where act.agent_id = ?
                and act.transaction_type_enum = ?
                and act.transaction_date = ?
                and act.status_enum <> ?
                """;
        return zeroIfNull(this.jdbcTemplate.queryForObject(sql, BigDecimal.class, agentId, transactionType.getValue(), businessDate,
                AgentCollectionTransactionStatusType.REVERSED.getValue()));
    }

    @Override
    public AgentCollectionSummaryData retrieveAgentSummary(final Long agentId, final LocalDate businessDate) {
        final LocalDate effectiveBusinessDate = businessDate == null ? DateUtils.getBusinessLocalDate() : businessDate;
        final AgentData agent = this.agentReadPlatformService.retrieveAgent(agentId);
        final BigDecimal currentCashInHand = retrieveCurrentCashInHand(agentId);
        final BigDecimal todaysLoanCollections = retrieveDailyCollectionTotal(agentId, AgentTransactionType.LOAN_REPAYMENT,
                effectiveBusinessDate);
        final BigDecimal todaysSavingsCollections = retrieveDailyCollectionTotal(agentId, AgentTransactionType.SAVINGS_DEPOSIT,
                effectiveBusinessDate);
        final BigDecimal todaysTotalCollections = retrieveDailyCollectionTotal(agentId, effectiveBusinessDate);
        final BigDecimal pendingSettlementAmount = retrievePendingSettlementAmount(agentId);
        final Long pendingTransactionCount = retrievePendingTransactionCount(agentId);
        final LocalDate lastSettlementDate = retrieveLastSettlementDate(agentId);

        return AgentCollectionSummaryData.builder().agent(agent).businessDate(effectiveBusinessDate).currentCashInHand(currentCashInHand)
                .todaysLoanCollections(todaysLoanCollections).todaysSavingsCollections(todaysSavingsCollections)
                .todaysTotalCollections(todaysTotalCollections).pendingSettlementAmount(pendingSettlementAmount)
                .pendingTransactionCount(pendingTransactionCount).lastSettlementDate(lastSettlementDate)
                .remainingLoanDailyLimit(
                        nonNegative(agent.getLoanCollectionLimit().getMaximumDailyAmount().subtract(todaysLoanCollections)))
                .remainingSavingsDailyLimit(
                        nonNegative(agent.getSavingsCollectionLimit().getMaximumDailyAmount().subtract(todaysSavingsCollections)))
                .remainingTotalDailyLimit(nonNegative(agent.getMaximumDailyTotalCollection().subtract(todaysTotalCollections)))
                .remainingCashCapacity(nonNegative(agent.getMaximumCashInHand().subtract(currentCashInHand))).build();
    }

    @Override
    public Page<AgentCollectionTransactionData> retrieveCollections(final AgentCollectionSearchParameters searchParameters) {
        return retrieveCollectionPage(searchParameters, false);
    }

    @Override
    public Page<AgentCollectionTransactionData> retrieveStatement(final AgentCollectionSearchParameters searchParameters) {
        return retrieveCollectionPage(searchParameters, true);
    }

    private Page<AgentCollectionTransactionData> retrieveCollectionPage(final AgentCollectionSearchParameters searchParameters,
            final boolean includeRunningBalance) {
        this.sqlValidator.validate(searchParameters.getOrderBy());
        this.sqlValidator.validate(searchParameters.getSortOrder());

        final AgentCollectionTransactionMapper mapper = new AgentCollectionTransactionMapper();
        final List<Object> params = new ArrayList<>();
        final StringBuilder sqlBuilder = new StringBuilder("select ").append(mapper.schema()).append(" where o.hierarchy like ?");
        params.add(this.context.authenticatedUser().getOffice().getHierarchy() + "%");
        addCollectionFilters(sqlBuilder, params, searchParameters);
        sqlBuilder.append(" order by ").append(resolveCollectionOrderBy(searchParameters.getOrderBy())).append(" ")
                .append(resolveSortOrder(searchParameters.getSortOrder()));

        if (searchParameters.hasLimit()) {
            sqlBuilder.append(" ");
            if (searchParameters.hasOffset()) {
                sqlBuilder.append(this.sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlBuilder.append(this.sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        final Page<AgentCollectionTransactionData> page = this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(),
                params.toArray(), mapper);
        if (!includeRunningBalance) {
            return page;
        }
        BigDecimal runningBalance = BigDecimal.ZERO;
        final List<AgentCollectionTransactionData> statementItems = new ArrayList<>();
        for (final AgentCollectionTransactionData transaction : page.getPageItems()) {
            runningBalance = runningBalance.add(cashImpactAmount(transaction));
            statementItems.add(transaction.toBuilder().runningBalance(runningBalance).build());
        }
        return new Page<>(statementItems, page.getTotalFilteredRecords());
    }

    private void addCollectionFilters(final StringBuilder sqlBuilder, final List<Object> params,
            final AgentCollectionSearchParameters searchParameters) {
        if (searchParameters.hasAgentId()) {
            sqlBuilder.append(" and act.agent_id = ?");
            params.add(searchParameters.getAgentId());
        }
        if (searchParameters.hasOfficeId()) {
            sqlBuilder.append(" and act.office_id = ?");
            params.add(searchParameters.getOfficeId());
        }
        if (searchParameters.hasBusinessDate()) {
            sqlBuilder.append(" and act.transaction_date = ?");
            params.add(searchParameters.getBusinessDate());
        }
        if (searchParameters.hasFromDate()) {
            sqlBuilder.append(" and act.transaction_date >= ?");
            params.add(searchParameters.getFromDate());
        }
        if (searchParameters.hasToDate()) {
            sqlBuilder.append(" and act.transaction_date <= ?");
            params.add(searchParameters.getToDate());
        }
        final Integer transactionType = parseTransactionType(searchParameters.getTransactionType());
        if (transactionType != null) {
            sqlBuilder.append(" and act.transaction_type_enum = ?");
            params.add(transactionType);
        }
        final Integer status = parseCollectionStatus(searchParameters.getStatus());
        if (status != null) {
            sqlBuilder.append(" and act.status_enum = ?");
            params.add(status);
        }
        if (searchParameters.hasSettlementId()) {
            sqlBuilder.append(" and act.settlement_id = ?");
            params.add(searchParameters.getSettlementId());
        }
    }

    private BigDecimal retrievePendingSettlementAmount(final Long agentId) {
        final String sql = """
                select coalesce(sum(act.amount), 0)
                from m_agent_collection_transaction act
                where act.agent_id = ?
                and act.status_enum = ?
                and act.settlement_id is null
                """;
        return zeroIfNull(
                this.jdbcTemplate.queryForObject(sql, BigDecimal.class, agentId, AgentCollectionTransactionStatusType.PENDING.getValue()));
    }

    private Long retrievePendingTransactionCount(final Long agentId) {
        final String sql = """
                select count(act.id)
                from m_agent_collection_transaction act
                where act.agent_id = ?
                and act.status_enum = ?
                and act.settlement_id is null
                """;
        return this.jdbcTemplate.queryForObject(sql, Long.class, agentId, AgentCollectionTransactionStatusType.PENDING.getValue());
    }

    private LocalDate retrieveLastSettlementDate(final Long agentId) {
        final String sql = "select max(act.settled_date) from m_agent_collection_transaction act where act.agent_id = ?";
        try {
            return this.jdbcTemplate.queryForObject(sql, LocalDate.class, agentId);
        } catch (final EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Integer parseTransactionType(final String transactionType) {
        if (StringUtils.isBlank(transactionType) || "all".equalsIgnoreCase(transactionType)) {
            return null;
        }
        if ("LOAN_REPAYMENT".equalsIgnoreCase(transactionType)) {
            return AgentTransactionType.LOAN_REPAYMENT.getValue();
        }
        if ("SAVINGS_DEPOSIT".equalsIgnoreCase(transactionType)) {
            return AgentTransactionType.SAVINGS_DEPOSIT.getValue();
        }
        throw new UnrecognizedQueryParamException("transactionType", transactionType,
                new Object[] { "LOAN_REPAYMENT", "SAVINGS_DEPOSIT", "all" });
    }

    private Integer parseCollectionStatus(final String status) {
        if (StringUtils.isBlank(status) || "all".equalsIgnoreCase(status)) {
            return null;
        }
        if ("PENDING".equalsIgnoreCase(status)) {
            return AgentCollectionTransactionStatusType.PENDING.getValue();
        }
        if ("SETTLED".equalsIgnoreCase(status)) {
            return AgentCollectionTransactionStatusType.SETTLED.getValue();
        }
        if ("REVERSED".equalsIgnoreCase(status)) {
            return AgentCollectionTransactionStatusType.REVERSED.getValue();
        }
        if ("DISPUTED".equalsIgnoreCase(status)) {
            return AgentCollectionTransactionStatusType.DISPUTED.getValue();
        }
        throw new UnrecognizedQueryParamException("status", status, new Object[] { "PENDING", "SETTLED", "REVERSED", "DISPUTED", "all" });
    }

    private String resolveCollectionOrderBy(final String orderBy) {
        if (StringUtils.isBlank(orderBy)) {
            return "act.transaction_date, act.id";
        }
        return switch (orderBy) {
            case "id" -> "act.id";
            case "agentId" -> "act.agent_id";
            case "officeId" -> "act.office_id";
            case "transactionDate" -> "act.transaction_date";
            case "transactionType" -> "act.transaction_type_enum";
            case "status" -> "act.status_enum";
            case "amount" -> "act.amount";
            default -> throw new UnrecognizedQueryParamException("orderBy", orderBy,
                    new Object[] { "id", "agentId", "officeId", "transactionDate", "transactionType", "status", "amount" });
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

    private BigDecimal cashImpactAmount(final AgentCollectionTransactionData transaction) {
        if (transaction.getStatus() == null || transaction.getStatus().getId() == null) {
            return BigDecimal.ZERO;
        }
        if (transaction.getStatus().getId().intValue() == AgentCollectionTransactionStatusType.PENDING.getValue()
                || transaction.getStatus().getId().intValue() == AgentCollectionTransactionStatusType.DISPUTED.getValue()) {
            return transaction.getAmount();
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal nonNegative(final BigDecimal amount) {
        return amount.signum() < 0 ? BigDecimal.ZERO : amount;
    }

    private BigDecimal zeroIfNull(final BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static final class AgentCollectionTransactionMapper implements RowMapper<AgentCollectionTransactionData> {

        public String schema() {
            return "act.id as id, act.agent_id as agentId, act.office_id as officeId, o.name as officeName,ma.username as agentName, "
                    + "act.transaction_type_enum as transactionTypeEnum, act.loan_id as loanId, "
                    + "act.loan_transaction_id as loanTransactionId, act.savings_account_id as savingsAccountId, "
                    + "act.savings_transaction_id as savingsTransactionId, act.payment_type_id as paymentTypeId, "
                    + "pt.value as paymentTypeName, act.amount as amount, act.currency_code as currencyCode, "
                    + "act.transaction_date as transactionDate, act.status_enum as statusEnum, act.settlement_id as settlementId, "
                    + "act.settled_date as settledDate, act.external_id as externalId, act.mobile_reference as mobileReference "
                    + "from m_agent_collection_transaction act join m_agent a on a.id = act.agent_id "
                    + "join m_office o on o.id = act.office_id join m_payment_type pt on pt.id = act.payment_type_id "
                    + "join m_appuser as ma on  ma.id = a.appuser_id ";

        }

        @Override
        public AgentCollectionTransactionData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Integer transactionTypeEnum = JdbcSupport.getInteger(rs, "transactionTypeEnum");
            final Integer statusEnum = JdbcSupport.getInteger(rs, "statusEnum");
            return AgentCollectionTransactionData.builder().id(rs.getLong("id")).agentId(rs.getLong("agentId"))
                    .officeId(rs.getLong("officeId")).officeName(rs.getString("officeName"))
                    .transactionType(AgentEnumerations.transactionType(transactionTypeEnum)).loanId(JdbcSupport.getLong(rs, "loanId"))
                    .loanTransactionId(JdbcSupport.getLong(rs, "loanTransactionId"))
                    .savingsAccountId(JdbcSupport.getLong(rs, "savingsAccountId"))
                    .savingsTransactionId(JdbcSupport.getLong(rs, "savingsTransactionId")).paymentTypeId(rs.getLong("paymentTypeId"))
                    .paymentTypeName(rs.getString("paymentTypeName")).amount(rs.getBigDecimal("amount"))
                    .currencyCode(rs.getString("currencyCode")).transactionDate(JdbcSupport.getLocalDate(rs, "transactionDate"))
                    .status(AgentEnumerations.collectionTransactionStatus(statusEnum)).settlementId(JdbcSupport.getLong(rs, "settlementId"))
                    .settledDate(JdbcSupport.getLocalDate(rs, "settledDate")).externalId(rs.getString("externalId"))
                    .mobileReference(rs.getString("mobileReference")).agentName(rs.getString("agentName")).build();
        }
    }
}
