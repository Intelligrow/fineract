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
package org.apache.fineract.lead.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.lead.data.AvailableLeadStageData;
import org.apache.fineract.lead.data.LeadProcessDefinitionData;
import org.apache.fineract.lead.data.LeadProcessDefinitionVersionData;
import org.apache.fineract.lead.service.LeadProcessReadPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/leadprocesses")
@Component
@RequiredArgsConstructor
@Tag(name = "Lead Processes", description = "Configure lead process definitions and stage sequences")
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class LeadProcessApiResource {

    private static final String LEAD_PROCESS_RESOURCE_NAME = "LEAD_PROCESS";

    private final PlatformSecurityContext context;
    private final LeadProcessReadPlatformService readPlatformService;
    private final PortfolioCommandSourceWritePlatformService commandSourceWritePlatformService;

    @GET
    @Path("available-stages")
    @Produces({ MediaType.APPLICATION_JSON })
    public List<AvailableLeadStageData> retrieveAvailableStages() {
        context.authenticatedUser().validateHasReadPermission(LEAD_PROCESS_RESOURCE_NAME);
        return readPlatformService.retrieveAvailableStages();
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    public List<LeadProcessDefinitionData> retrieveAllProcessDefinitions() {
        context.authenticatedUser().validateHasReadPermission(LEAD_PROCESS_RESOURCE_NAME);
        return readPlatformService.retrieveAllProcessDefinitions();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult createDraftProcessDefinition(String jsonRequestBody) {
        final CommandWrapper command = LeadCommandWrapperBuilder.createLeadProcess(jsonRequestBody);
        return commandSourceWritePlatformService.logCommandSource(command);
    }

    @GET
    @Path("{processDefinitionId}")
    @Produces({ MediaType.APPLICATION_JSON })
    public LeadProcessDefinitionData retrieveProcessDefinition(@PathParam("processDefinitionId") Long processDefinitionId) {
        context.authenticatedUser().validateHasReadPermission(LEAD_PROCESS_RESOURCE_NAME);
        return readPlatformService.retrieveProcessDefinition(processDefinitionId);
    }

    @GET
    @Path("{processDefinitionId}/versions")
    @Produces({ MediaType.APPLICATION_JSON })
    public List<LeadProcessDefinitionVersionData> retrieveProcessDefinitionVersions(
            @PathParam("processDefinitionId") Long processDefinitionId) {
        context.authenticatedUser().validateHasReadPermission(LEAD_PROCESS_RESOURCE_NAME);
        return readPlatformService.retrieveProcessDefinitionVersions(processDefinitionId);
    }

    @POST
    @Path("{processDefinitionId}/versions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult createDraftProcessDefinitionVersion(@PathParam("processDefinitionId") Long processDefinitionId,
            String jsonRequestBody) {
        final CommandWrapper command = LeadCommandWrapperBuilder.createLeadProcessVersion(processDefinitionId, jsonRequestBody);
        return commandSourceWritePlatformService.logCommandSource(command);
    }

    @GET
    @Path("{processDefinitionId}/versions/{versionId}")
    @Produces({ MediaType.APPLICATION_JSON })
    public LeadProcessDefinitionVersionData retrieveProcessDefinitionVersion(@PathParam("processDefinitionId") Long processDefinitionId,
            @PathParam("versionId") Long versionId) {
        context.authenticatedUser().validateHasReadPermission(LEAD_PROCESS_RESOURCE_NAME);
        return readPlatformService.retrieveProcessDefinitionVersion(processDefinitionId, versionId);
    }

    @PUT
    @Path("{processDefinitionId}/versions/{versionId}/stages")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult configureStageDefinitions(@PathParam("processDefinitionId") Long processDefinitionId,
            @PathParam("versionId") Long versionId, String jsonRequestBody) {
        final CommandWrapper command = LeadCommandWrapperBuilder.configureLeadProcessStages(processDefinitionId, versionId,
                jsonRequestBody);
        return commandSourceWritePlatformService.logCommandSource(command);
    }

    @POST
    @Path("{processDefinitionId}/versions/{versionId}/publish")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult publishProcessDefinitionVersion(@PathParam("processDefinitionId") Long processDefinitionId,
            @PathParam("versionId") Long versionId, String jsonRequestBody) {
        final CommandWrapper command = LeadCommandWrapperBuilder.publishLeadProcess(processDefinitionId, versionId, jsonRequestBody);
        return commandSourceWritePlatformService.logCommandSource(command);
    }
}
