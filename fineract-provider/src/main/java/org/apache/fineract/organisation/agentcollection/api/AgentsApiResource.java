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
package org.apache.fineract.organisation.agentcollection.api;

import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_COLLECTION_RESOURCE_NAME;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_COLLECTION_SUMMARY_RESPONSE_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_COLLECTION_TRANSACTION_RESPONSE_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_RESOURCE_NAME;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_RESPONSE_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_SETTLEMENT_RESOURCE_NAME;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_SETTLEMENT_RESPONSE_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_TEMPLATE_RESPONSE_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.activateCommandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.agentIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.approveAgentSettlementPermissionName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.approveCommandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.associationsParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.businessDateParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.cancelAgentSettlementPermissionName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.cancelCommandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.commandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.dateFormatParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.deactivateCommandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.fromDateParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.limitParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.localeParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.officeIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.offsetParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.orderByParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.readAgentCashBalancePermissionName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.rejectAgentSettlementPermissionName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.rejectCommandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.settlementIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.sortOrderParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.statusParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.submitAgentSettlementPermissionName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.submitCommandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.toDateParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.transactionTypeParamName;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.annotation.AlternativeOperationId;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.api.DateParam;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.DateFormat;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionSearchParameters;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionSummaryData;
import org.apache.fineract.organisation.agentcollection.data.AgentCollectionTransactionData;
import org.apache.fineract.organisation.agentcollection.data.AgentData;
import org.apache.fineract.organisation.agentcollection.data.AgentSettlementCommand;
import org.apache.fineract.organisation.agentcollection.data.AgentSettlementData;
import org.apache.fineract.organisation.agentcollection.data.AgentSettlementSearchParameters;
import org.apache.fineract.organisation.agentcollection.data.AgentTemplateData;
import org.apache.fineract.organisation.agentcollection.serialization.AgentSettlementCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionReadPlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentCommandWrapperBuilder;
import org.apache.fineract.organisation.agentcollection.service.AgentReadPlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentSettlementReadPlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentSettlementWritePlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/agents")
@Component
@Tag(name = "Agent Collection", description = "Agent configuration for loan repayment and savings deposit cash collection.")
@RequiredArgsConstructor
public class AgentsApiResource {

    private final PlatformSecurityContext context;
    private final AgentReadPlatformService agentReadPlatformService;
    private final AgentCollectionReadPlatformService agentCollectionReadPlatformService;
    private final AgentSettlementReadPlatformService agentSettlementReadPlatformService;
    private final AgentSettlementWritePlatformService agentSettlementWritePlatformService;
    private final AgentSettlementCommandFromApiJsonDeserializer agentSettlementCommandFromApiJsonDeserializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<AgentData> toApiJsonSerializer;
    private final DefaultToApiJsonSerializer<AgentCollectionSummaryData> summaryToApiJsonSerializer;
    private final DefaultToApiJsonSerializer<AgentCollectionTransactionData> transactionToApiJsonSerializer;
    private final DefaultToApiJsonSerializer<AgentSettlementData> settlementToApiJsonSerializer;
    private final DefaultToApiJsonSerializer<AgentTemplateData> templateToApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List agents", operationId = "retrieveAllAgents", description = "Lists agent collection configurations with office, status, pagination, and sorting filters.")
    @AlternativeOperationId("retrieveAgents")
    public String retrieveAgents(@Context final UriInfo uriInfo,
            @QueryParam(officeIdParamName) @Parameter(description = "officeId") final Long officeId,
            @QueryParam(statusParamName) @Parameter(description = "active, inactive, or all") final String status,
            @QueryParam(offsetParamName) @Parameter(description = "offset") final Integer offset,
            @QueryParam(limitParamName) @Parameter(description = "limit") final Integer limit,
            @QueryParam(orderByParamName) @Parameter(description = "id, appUserName, staffName, officeName, status, or currencyCode") final String orderBy,
            @QueryParam(sortOrderParamName) @Parameter(description = "ASC or DESC") final String sortOrder) {
        this.context.authenticatedUser().validateHasReadPermission(AGENT_RESOURCE_NAME);
        final SearchParameters searchParameters = SearchParameters.builder().officeId(officeId).status(status).offset(offset).limit(limit)
                .orderBy(orderBy).sortOrder(sortOrder).build();
        final Page<AgentData> agents = this.agentReadPlatformService.retrieveAllAgents(searchParameters);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, agents, AGENT_RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("collections")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List agent collections", operationId = "retrieveAgentCollections", description = "Lists agent collection transactions with agent, office, date, transaction type, status, and pagination filters.")
    public String retrieveCollections(@Context final UriInfo uriInfo,
            @QueryParam(agentIdParamName) @Parameter(description = "agentId") final Long agentId,
            @QueryParam(officeIdParamName) @Parameter(description = "officeId") final Long officeId,
            @QueryParam(businessDateParamName) @Parameter(description = "businessDate") final DateParam businessDateParam,
            @QueryParam(fromDateParamName) @Parameter(description = "fromDate") final DateParam fromDateParam,
            @QueryParam(toDateParamName) @Parameter(description = "toDate") final DateParam toDateParam,
            @QueryParam(transactionTypeParamName) @Parameter(description = "LOAN_REPAYMENT, SAVINGS_DEPOSIT, or all") final String transactionType,
            @QueryParam(statusParamName) @Parameter(description = "PENDING, SETTLED, REVERSED, DISPUTED, or all") final String status,
            @QueryParam(settlementIdParamName) @Parameter(description = "settlementId") final Long settlementId,
            @QueryParam(offsetParamName) @Parameter(description = "offset") final Integer offset,
            @QueryParam(limitParamName) @Parameter(description = "limit") final Integer limit,
            @QueryParam(orderByParamName) @Parameter(description = "id, agentId, officeId, transactionDate, transactionType, status, or amount") final String orderBy,
            @QueryParam(sortOrderParamName) @Parameter(description = "ASC or DESC") final String sortOrder,
            @QueryParam(localeParamName) @Parameter(description = "locale") final String locale,
            @QueryParam(dateFormatParamName) @Parameter(description = "dateFormat") final String rawDateFormat) {
        this.context.authenticatedUser().validateHasReadPermission(AGENT_COLLECTION_RESOURCE_NAME);
        final AgentCollectionSearchParameters searchParameters = collectionSearchParameters(agentId, officeId, businessDateParam,
                fromDateParam, toDateParam, transactionType, status, settlementId, offset, limit, orderBy, sortOrder, locale,
                rawDateFormat);
        final Page<AgentCollectionTransactionData> collections = this.agentCollectionReadPlatformService
                .retrieveCollections(searchParameters);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.transactionToApiJsonSerializer.serialize(settings, collections, AGENT_COLLECTION_TRANSACTION_RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("settlements")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List agent settlements", operationId = "retrieveAgentSettlements", description = "Lists agent settlement requests with agent, office, status, date, pagination, and sorting filters.")
    public String retrieveSettlements(@Context final UriInfo uriInfo,
            @QueryParam(agentIdParamName) @Parameter(description = "agentId") final Long agentId,
            @QueryParam(officeIdParamName) @Parameter(description = "officeId") final Long officeId,
            @QueryParam(fromDateParamName) @Parameter(description = "fromDate") final DateParam fromDateParam,
            @QueryParam(toDateParamName) @Parameter(description = "toDate") final DateParam toDateParam,
            @QueryParam(statusParamName) @Parameter(description = "DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELLED, or all") final String status,
            @QueryParam(offsetParamName) @Parameter(description = "offset") final Integer offset,
            @QueryParam(limitParamName) @Parameter(description = "limit") final Integer limit,
            @QueryParam(orderByParamName) @Parameter(description = "id, agentId, officeId, settlementDate, status, expectedAmount, or submittedAmount") final String orderBy,
            @QueryParam(sortOrderParamName) @Parameter(description = "ASC or DESC") final String sortOrder,
            @QueryParam(localeParamName) @Parameter(description = "locale") final String locale,
            @QueryParam(dateFormatParamName) @Parameter(description = "dateFormat") final String rawDateFormat) {
        this.context.authenticatedUser().validateHasReadPermission(AGENT_SETTLEMENT_RESOURCE_NAME);
        final AgentSettlementSearchParameters searchParameters = settlementSearchParameters(agentId, officeId, fromDateParam, toDateParam,
                status, offset, limit, orderBy, sortOrder, locale, rawDateFormat);
        final Page<AgentSettlementData> settlements = this.agentSettlementReadPlatformService.retrieveSettlements(searchParameters);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.settlementToApiJsonSerializer.serialize(settings, settlements, AGENT_SETTLEMENT_RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve agent template", operationId = "retrieveAgentTemplate", description = "Retrieves lookup options for agent creation and filters. "
            + "Use associations=all or a comma-separated list: offices, staff, users, currencies, paymentTypes, agents.")
    public String retrieveAgentTemplate(@Context final UriInfo uriInfo,
            @QueryParam(associationsParamName) @Parameter(description = "all, or comma-separated values: offices, staff, users, currencies, paymentTypes, agents") final String associations) {
        this.context.authenticatedUser().validateHasReadPermission(AGENT_RESOURCE_NAME);
        final AgentTemplateData template = this.agentReadPlatformService.retrieveTemplate(associations);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.templateToApiJsonSerializer.serialize(settings, template, AGENT_TEMPLATE_RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("settlements/{settlementId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve an agent settlement", operationId = "retrieveOneAgentSettlement", description = "Retrieves one settlement request with its selected collection transactions.")
    public String retrieveSettlement(@PathParam("settlementId") @Parameter(description = "settlementId") final Long settlementId,
            @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(AGENT_SETTLEMENT_RESOURCE_NAME);
        final AgentSettlementData settlement = this.agentSettlementReadPlatformService.retrieveSettlement(settlementId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.settlementToApiJsonSerializer.serialize(settings, settlement, AGENT_SETTLEMENT_RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("{agentId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve an agent", operationId = "retrieveOneAgent", description = "Retrieves one agent collection configuration.")
    @AlternativeOperationId("retrieveAgent")
    public String retrieveAgent(@PathParam("agentId") @Parameter(description = "agentId") final Long agentId,
            @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(AGENT_RESOURCE_NAME);
        final AgentData agent = this.agentReadPlatformService.retrieveAgent(agentId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, agent, AGENT_RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("{agentId}/summary")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve agent collection summary", operationId = "retrieveAgentCollectionSummary", description = "Retrieves cash-in-hand, daily collections, pending settlement, and remaining limit summary for one agent.")
    public String retrieveAgentSummary(@PathParam("agentId") @Parameter(description = "agentId") final Long agentId,
            @QueryParam(businessDateParamName) @Parameter(description = "businessDate") final DateParam businessDateParam,
            @QueryParam(localeParamName) @Parameter(description = "locale") final String locale,
            @QueryParam(dateFormatParamName) @Parameter(description = "dateFormat") final String rawDateFormat,
            @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasPermissionTo(readAgentCashBalancePermissionName);
        final LocalDate businessDate = localDate(businessDateParam, businessDateParamName, locale, rawDateFormat);
        final AgentCollectionSummaryData summary = this.agentCollectionReadPlatformService.retrieveAgentSummary(agentId, businessDate);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.summaryToApiJsonSerializer.serialize(settings, summary, AGENT_COLLECTION_SUMMARY_RESPONSE_DATA_PARAMETERS);
    }

    @GET
    @Path("{agentId}/statement")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve agent collection statement", operationId = "retrieveAgentCollectionStatement", description = "Retrieves agent collection transactions with calculated running balance for statement views.")
    public String retrieveAgentStatement(@PathParam("agentId") @Parameter(description = "agentId") final Long agentId,
            @QueryParam(officeIdParamName) @Parameter(description = "officeId") final Long officeId,
            @QueryParam(businessDateParamName) @Parameter(description = "businessDate") final DateParam businessDateParam,
            @QueryParam(fromDateParamName) @Parameter(description = "fromDate") final DateParam fromDateParam,
            @QueryParam(toDateParamName) @Parameter(description = "toDate") final DateParam toDateParam,
            @QueryParam(transactionTypeParamName) @Parameter(description = "LOAN_REPAYMENT, SAVINGS_DEPOSIT, or all") final String transactionType,
            @QueryParam(statusParamName) @Parameter(description = "PENDING, SETTLED, REVERSED, DISPUTED, or all") final String status,
            @QueryParam(settlementIdParamName) @Parameter(description = "settlementId") final Long settlementId,
            @QueryParam(offsetParamName) @Parameter(description = "offset") final Integer offset,
            @QueryParam(limitParamName) @Parameter(description = "limit") final Integer limit,
            @QueryParam(orderByParamName) @Parameter(description = "id, agentId, officeId, transactionDate, transactionType, status, or amount") final String orderBy,
            @QueryParam(sortOrderParamName) @Parameter(description = "ASC or DESC") final String sortOrder,
            @QueryParam(localeParamName) @Parameter(description = "locale") final String locale,
            @QueryParam(dateFormatParamName) @Parameter(description = "dateFormat") final String rawDateFormat,
            @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(AGENT_COLLECTION_RESOURCE_NAME);
        final AgentCollectionSearchParameters searchParameters = collectionSearchParameters(agentId, officeId, businessDateParam,
                fromDateParam, toDateParam, transactionType, status, settlementId, offset, limit, orderBy, sortOrder, locale,
                rawDateFormat);
        final Page<AgentCollectionTransactionData> statement = this.agentCollectionReadPlatformService.retrieveStatement(searchParameters);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.transactionToApiJsonSerializer.serialize(settings, statement, AGENT_COLLECTION_TRANSACTION_RESPONSE_DATA_PARAMETERS);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create an agent", operationId = "createAgent", description = "Creates an agent collection configuration.")
    @RequestBody(required = true)
    public String createAgent(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new AgentCommandWrapperBuilder().createAgent(apiRequestBodyAsJson);
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("settlements")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create a draft agent settlement", operationId = "createAgentSettlement", description = "Creates a draft settlement request from selected pending agent collection transactions.")
    @RequestBody(required = true)
    public String createSettlement(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasCreatePermission(AGENT_SETTLEMENT_RESOURCE_NAME);
        final AgentSettlementCommand command = this.agentSettlementCommandFromApiJsonDeserializer
                .validateAndParseCreate(apiRequestBodyAsJson);
        final CommandProcessingResult result = this.agentSettlementWritePlatformService.createDraftSettlement(command);
        return this.settlementToApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("settlements/{settlementId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Submit, approve, reject, or cancel an agent settlement", operationId = "handleAgentSettlementCommands", description = "Supports command=submit, command=approve, command=reject, and command=cancel.")
    @RequestBody(required = false)
    public String handleSettlementCommands(@PathParam("settlementId") @Parameter(description = "settlementId") final Long settlementId,
            @QueryParam(commandParamName) @Parameter(description = "command") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        CommandProcessingResult result = null;


        if (is(commandParam, submitCommandParamName)) {
            this.context.authenticatedUser().validateHasPermissionTo(submitAgentSettlementPermissionName);
            result = this.agentSettlementWritePlatformService.submitSettlement(settlementId);
        } else if (is(commandParam, approveCommandParamName)) {
            this.context.authenticatedUser().validateHasPermissionTo(approveAgentSettlementPermissionName);
            final CommandWrapper commandRequest = new AgentCommandWrapperBuilder().ApproveAgentSettlement(settlementId, apiRequestBodyAsJson);
            result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        } else if (is(commandParam, rejectCommandParamName)) {
            this.context.authenticatedUser().validateHasPermissionTo(rejectAgentSettlementPermissionName);
            final AgentSettlementCommand command = this.agentSettlementCommandFromApiJsonDeserializer.validateAndParseReject(apiRequestBodyAsJson);
            result = this.agentSettlementWritePlatformService.rejectSettlement(settlementId, command.getRejectionReason());
        } else if (is(commandParam, cancelCommandParamName)) {
            this.context.authenticatedUser().validateHasPermissionTo(cancelAgentSettlementPermissionName);
            result = this.agentSettlementWritePlatformService.cancelDraftSettlement(settlementId);
        }

        if (result == null) {
            throw new UnrecognizedQueryParamException(commandParamName, commandParam,
                    new Object[] { submitCommandParamName, approveCommandParamName, rejectCommandParamName, cancelCommandParamName });
        }
        return this.settlementToApiJsonSerializer.serialize(result);
    }

    @PUT
    @Path("{agentId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update an agent", operationId = "updateAgent", description = "Updates an agent collection configuration.")
    @RequestBody(required = true)
    public String updateAgent(@PathParam("agentId") @Parameter(description = "agentId") final Long agentId,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new AgentCommandWrapperBuilder().updateAgent(agentId, apiRequestBodyAsJson);
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return this.toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("{agentId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Activate or deactivate an agent", operationId = "handleAgentCommands", description = "Supports command=activate and command=deactivate.")
    @AlternativeOperationId("handleAgentCommands")
    @RequestBody(required = false)
    public String handleCommands(@PathParam("agentId") @Parameter(description = "agentId") final Long agentId,
            @QueryParam(commandParamName) @Parameter(description = "command") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final String jsonApiRequest = StringUtils.defaultIfBlank(apiRequestBodyAsJson, "{}");
        final AgentCommandWrapperBuilder builder = new AgentCommandWrapperBuilder();

        CommandWrapper commandRequest = null;
        if (is(commandParam, activateCommandParamName)) {
            commandRequest = builder.activateAgent(agentId, jsonApiRequest);
        } else if (is(commandParam, deactivateCommandParamName)) {
            commandRequest = builder.deactivateAgent(agentId, jsonApiRequest);
        }

        if (commandRequest == null) {
            throw new UnrecognizedQueryParamException(commandParamName, commandParam,
                    new Object[] { activateCommandParamName, deactivateCommandParamName });
        }

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return this.toApiJsonSerializer.serialize(result);
    }

    private boolean is(final String commandParam, final String commandValue) {
        return StringUtils.isNotBlank(commandParam) && commandParam.trim().equalsIgnoreCase(commandValue);
    }

    private AgentCollectionSearchParameters collectionSearchParameters(final Long agentId, final Long officeId,
            final DateParam businessDateParam, final DateParam fromDateParam, final DateParam toDateParam, final String transactionType,
            final String status, final Long settlementId, final Integer offset, final Integer limit, final String orderBy,
            final String sortOrder, final String locale, final String rawDateFormat) {
        return AgentCollectionSearchParameters.builder().agentId(agentId).officeId(officeId)
                .businessDate(localDate(businessDateParam, businessDateParamName, locale, rawDateFormat))
                .fromDate(localDate(fromDateParam, fromDateParamName, locale, rawDateFormat))
                .toDate(localDate(toDateParam, toDateParamName, locale, rawDateFormat)).transactionType(transactionType).status(status)
                .settlementId(settlementId).offset(offset).limit(limit).orderBy(orderBy).sortOrder(sortOrder).build();
    }

    private AgentSettlementSearchParameters settlementSearchParameters(final Long agentId, final Long officeId,
            final DateParam fromDateParam, final DateParam toDateParam, final String status, final Integer offset, final Integer limit,
            final String orderBy, final String sortOrder, final String locale, final String rawDateFormat) {
        return AgentSettlementSearchParameters.builder().agentId(agentId).officeId(officeId)
                .fromDate(localDate(fromDateParam, fromDateParamName, locale, rawDateFormat))
                .toDate(localDate(toDateParam, toDateParamName, locale, rawDateFormat)).status(status).offset(offset).limit(limit)
                .orderBy(orderBy).sortOrder(sortOrder).build();
    }

    private LocalDate localDate(final DateParam dateParam, final String parameterName, final String locale, final String rawDateFormat) {
        if (dateParam == null) {
            return null;
        }
        final DateFormat dateFormat = StringUtils.isBlank(rawDateFormat) ? null : new DateFormat(rawDateFormat);
        return dateParam.getDate(parameterName, dateFormat, locale);
    }
}
