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

import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_RESOURCE_NAME;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_RESPONSE_DATA_PARAMETERS;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.activateCommandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.commandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.deactivateCommandParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.limitParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.officeIdParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.offsetParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.orderByParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.sortOrderParamName;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.statusParamName;

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
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.annotation.AlternativeOperationId;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.agentcollection.data.AgentData;
import org.apache.fineract.organisation.agentcollection.service.AgentCommandWrapperBuilder;
import org.apache.fineract.organisation.agentcollection.service.AgentReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/agents")
@Component
@Tag(name = "Agent Collection", description = "Agent configuration for loan repayment and savings deposit cash collection.")
@RequiredArgsConstructor
public class AgentsApiResource {

    private final PlatformSecurityContext context;
    private final AgentReadPlatformService agentReadPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final DefaultToApiJsonSerializer<AgentData> toApiJsonSerializer;
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
}
