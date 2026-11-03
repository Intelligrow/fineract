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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionSearchParameters;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionTransactionData;
import org.apache.fineract.organisation.agentcollection.data.AgentSettlementData;
import org.apache.fineract.organisation.agentcollection.data.AgentSettlementSearchParameters;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlementStatusType;
import org.apache.fineract.organisation.agentcollection.exception.AgentConfigurationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
public class AgentSettlementReadPlatformServiceImpl implements AgentSettlementReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final AgentCollectionReadPlatformService agentCollectionReadPlatformService;
    private final PaginationHelper paginationHelper;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final SqlValidator sqlValidator;

    @Override
    public AgentSettlementData retrieveSettlement(final Long settlementId) {
        final AgentSettlementMapper mapper = new AgentSettlementMapper();
        final String sql = "select " + mapper.schema() + " where o.hierarchy like ? and ast.id = ?";
        try {
            final AgentSettlementData settlement = this.jdbcTemplate.queryForObject(sql, mapper,
                    this.context.authenticatedUser().getOffice().getHierarchy() + "%", settlementId);
            final Page<AgentCollectionTransactionData> transactions = this.agentCollectionReadPlatformService
                    .retrieveCollections(AgentCollectionSearchParameters.builder().settlementId(settlementId).orderBy("id").build());
            return settlement.toBuilder().transactions(transactions.getPageItems()).build();
        } catch (final EmptyResultDataAccessException e) {
            throw new AgentConfigurationException("settlement.not.found", "Agent settlement was not found.", settlementId, e);
        }
    }

    @Override
    public Page<AgentSettlementData> retrieveSettlements(final AgentSettlementSearchParameters searchParameters) {
        this.sqlValidator.validate(searchParameters.getOrderBy());
        this.sqlValidator.validate(searchParameters.getSortOrder());

        final AgentSettlementMapper mapper = new AgentSettlementMapper();
        final List<Object> params = new ArrayList<>();
        final StringBuilder sqlBuilder = new StringBuilder("select ").append(mapper.schema()).append(" where o.hierarchy like ?");
        params.add(this.context.authenticatedUser().getOffice().getHierarchy() + "%");
        addSettlementFilters(sqlBuilder, params, searchParameters);
        sqlBuilder.append(" order by ").append(resolveSettlementOrderBy(searchParameters.getOrderBy())).append(" ")
                .append(resolveSortOrder(searchParameters.getSortOrder()));

        if (searchParameters.hasLimit()) {
            sqlBuilder.append(" ");
            if (searchParameters.hasOffset()) {
                sqlBuilder.append(this.sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlBuilder.append(this.sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), params.toArray(), mapper);
    }

    private void addSettlementFilters(final StringBuilder sqlBuilder, final List<Object> params,
            final AgentSettlementSearchParameters searchParameters) {
        if (searchParameters.hasAgentId()) {
            sqlBuilder.append(" and ast.agent_id = ?");
            params.add(searchParameters.getAgentId());
        }
        if (searchParameters.hasOfficeId()) {
            sqlBuilder.append(" and ast.office_id = ?");
            params.add(searchParameters.getOfficeId());
        }
        if (searchParameters.hasFromDate()) {
            sqlBuilder.append(" and ast.settlement_date >= ?");
            params.add(searchParameters.getFromDate());
        }
        if (searchParameters.hasToDate()) {
            sqlBuilder.append(" and ast.settlement_date <= ?");
            params.add(searchParameters.getToDate());
        }
        final Integer status = parseSettlementStatus(searchParameters.getStatus());
        if (status != null) {
            sqlBuilder.append(" and ast.status_enum = ?");
            params.add(status);
        }
    }

    private Integer parseSettlementStatus(final String status) {
        if (StringUtils.isBlank(status) || "all".equalsIgnoreCase(status)) {
            return null;
        }
        if ("DRAFT".equalsIgnoreCase(status)) {
            return AgentSettlementStatusType.DRAFT.getValue();
        }
        if ("SUBMITTED".equalsIgnoreCase(status)) {
            return AgentSettlementStatusType.SUBMITTED.getValue();
        }
        if ("APPROVED".equalsIgnoreCase(status)) {
            return AgentSettlementStatusType.APPROVED.getValue();
        }
        if ("REJECTED".equalsIgnoreCase(status)) {
            return AgentSettlementStatusType.REJECTED.getValue();
        }
        if ("CANCELLED".equalsIgnoreCase(status)) {
            return AgentSettlementStatusType.CANCELLED.getValue();
        }
        throw new UnrecognizedQueryParamException("status", status,
		        "DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "CANCELLED", "all");
    }

    private String resolveSettlementOrderBy(final String orderBy) {
        if (StringUtils.isBlank(orderBy)) {
            return "ast.settlement_date, ast.id";
        }
        return switch (orderBy) {
            case "id" -> "ast.id";
            case "agentId" -> "ast.agent_id";
            case "officeId" -> "ast.office_id";
            case "settlementDate" -> "ast.settlement_date";
            case "status" -> "ast.status_enum";
            case "expectedAmount" -> "ast.expected_amount";
            case "submittedAmount" -> "ast.submitted_amount";
            default -> throw new UnrecognizedQueryParamException("orderBy", orderBy,
		            "id", "agentId", "officeId", "settlementDate", "status", "expectedAmount", "submittedAmount");
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

    private static final class AgentSettlementMapper implements RowMapper<AgentSettlementData> {

        public String schema() {
            return "ast.id as id, ast.agent_id as agentId,ma.username as agentName , ast.office_id as officeId, o.name as officeName, "
                    + "ast.settlement_date as settlementDate, ast.expected_amount as expectedAmount, "
                    + "ast.submitted_amount as submittedAmount, ast.currency_code as currencyCode, "
                    + "ast.status_enum as statusEnum, ast.reference_number as referenceNumber, ast.notes as notes, "
                    + "ast.submitted_by_user_id as submittedByUserId, ast.submitted_on_date as submittedOnDate, "
                    + "ast.approved_by_user_id as approvedByUserId, ast.approved_on_date as approvedOnDate, "
                    + "ast.rejected_by_user_id as rejectedByUserId, ast.rejected_on_date as rejectedOnDate, "
                    + "ast.rejection_reason as rejectionReason, ast.cancelled_by_user_id as cancelledByUserId, "
                    + "ast.cancelled_on_date as cancelledOnDate from m_agent_settlement ast " + "join m_office o on o.id = ast.office_id "
                    + "join m_agent a on a.id = ast.agent_id " + "join m_appuser as ma on ma.id = a.appuser_id ";
        }

        @Override
        public AgentSettlementData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Integer statusEnum = JdbcSupport.getInteger(rs, "statusEnum");
            return AgentSettlementData.builder().id(rs.getLong("id")).agentId(rs.getLong("agentId")).officeId(rs.getLong("officeId"))
                    .officeName(rs.getString("officeName")).settlementDate(JdbcSupport.getLocalDate(rs, "settlementDate"))
                    .expectedAmount(rs.getBigDecimal("expectedAmount")).submittedAmount(rs.getBigDecimal("submittedAmount"))
                    .currencyCode(rs.getString("currencyCode")).status(AgentEnumerations.settlementStatus(statusEnum))
                    .referenceNumber(rs.getString("referenceNumber")).notes(rs.getString("notes"))
                    .submittedByUserId(JdbcSupport.getLong(rs, "submittedByUserId"))
                    .submittedOnDate(JdbcSupport.getLocalDate(rs, "submittedOnDate"))
                    .approvedByUserId(JdbcSupport.getLong(rs, "approvedByUserId"))
                    .approvedOnDate(JdbcSupport.getLocalDate(rs, "approvedOnDate"))
                    .rejectedByUserId(JdbcSupport.getLong(rs, "rejectedByUserId"))
                    .rejectedOnDate(JdbcSupport.getLocalDate(rs, "rejectedOnDate")).rejectionReason(rs.getString("rejectionReason"))
                    .cancelledByUserId(JdbcSupport.getLong(rs, "cancelledByUserId"))
                    .cancelledOnDate(JdbcSupport.getLocalDate(rs, "cancelledOnDate")).agentName(rs.getString("agentName")).build();
        }
    }
}
