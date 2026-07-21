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
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.lead.data.LeadData;
import org.apache.fineract.lead.data.LeadStageData;
import org.apache.fineract.lead.data.LeadStagePrefillResponse;
import org.apache.fineract.lead.service.LeadReadPlatformService;
import org.apache.fineract.lead.service.LeadStageApplicationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/leads")
@Component
@RequiredArgsConstructor
@Tag(name = "Leads", description = "Lead architecture skeleton endpoints")
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class LeadApiResource {

    private static final String LEAD_RESOURCE_NAME = "LEAD";

    private final PlatformSecurityContext context;
    private final LeadReadPlatformService readPlatformService;
    private final LeadStageApplicationService stageApplicationService;
    private final PortfolioCommandSourceWritePlatformService commandSourceWritePlatformService;

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult create(String jsonRequestBody) {
        final CommandWrapper command = LeadCommandWrapperBuilder.createLead(jsonRequestBody);
        return commandSourceWritePlatformService.logCommandSource(command);
    }

    @GET
    @Path("{leadId}")
    @Produces({ MediaType.APPLICATION_JSON })
    public LeadData retrieveOne(@PathParam("leadId") Long leadId) {
        context.authenticatedUser().validateHasReadPermission(LEAD_RESOURCE_NAME);
        return readPlatformService.retrieveOne(leadId);
    }

    @PUT
    @Path("{leadId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult update(@PathParam("leadId") Long leadId, String jsonRequestBody) {
        final CommandWrapper command = LeadCommandWrapperBuilder.updateLead(leadId, jsonRequestBody);
        return commandSourceWritePlatformService.logCommandSource(command);
    }

    @GET
    @Path("{leadId}/stages")
    @Produces({ MediaType.APPLICATION_JSON })
    public java.util.List<LeadStageData> retrieveStages(@PathParam("leadId") Long leadId) {
        context.authenticatedUser().validateHasReadPermission(LEAD_RESOURCE_NAME);
        return readPlatformService.retrieveStages(leadId);
    }

    @POST
    @Path("{leadId}/stages/{stageInstanceId}/actions")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public CommandProcessingResult processStageAction(@PathParam("leadId") Long leadId, @PathParam("stageInstanceId") Long stageInstanceId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, String jsonRequestBody) {
        final CommandWrapper command = LeadCommandWrapperBuilder.processStage(leadId, stageInstanceId, jsonRequestBody, idempotencyKey);
        return commandSourceWritePlatformService.logCommandSource(command);
    }

    @POST
    @Path("{leadId}/stages/{stageInstanceId}/prefill")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public LeadStagePrefillResponse prefillStage(@PathParam("leadId") Long leadId, @PathParam("stageInstanceId") Long stageInstanceId,
            @HeaderParam("Idempotency-Key") String idempotencyKey, Map<String, Object> attributes) {
        final var user = context.getAuthenticatedUserIfPresent();
        final Long actingUserId = user == null ? null : user.getId();
        return stageApplicationService.prefill(leadId, stageInstanceId, idempotencyKey, attributes, actingUserId);
    }
}
