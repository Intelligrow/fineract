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

import java.util.Set;
import org.apache.fineract.commands.domain.CommandWrapper;

final class LeadCommandWrapperBuilder {

    static final String ENTITY_LEAD = "LEAD";
    static final String ENTITY_LEAD_STAGE = "LEAD_STAGE";
    static final String ENTITY_LEAD_PROCESS = "LEAD_PROCESS";

    private LeadCommandWrapperBuilder() {}

    static CommandWrapper createLead(String json) {
        return command("CREATE", ENTITY_LEAD, null, null, "/v1/leads", json, null);
    }

    static CommandWrapper updateLead(Long leadId, String json) {
        return command("UPDATE", ENTITY_LEAD, leadId, null, "/v1/leads/" + leadId, json, null);
    }

    static CommandWrapper processStage(Long leadId, Long stageInstanceId, String json, String idempotencyKey) {
        return command("PROCESS", ENTITY_LEAD_STAGE, leadId, stageInstanceId, "/v1/leads/" + leadId + "/stages/" + stageInstanceId, json,
                idempotencyKey);
    }

    static CommandWrapper submitStage(Long leadId, Long stageInstanceId, String json) {
        return command("SUBMIT", ENTITY_LEAD_STAGE, leadId, stageInstanceId, "/v1/leads/" + leadId + "/stages/" + stageInstanceId, json,
                null);
    }

    static CommandWrapper checkStage(Long leadId, Long stageInstanceId, String json) {
        return command("CHECK", ENTITY_LEAD_STAGE, leadId, stageInstanceId, "/v1/leads/" + leadId + "/stages/" + stageInstanceId, json,
                null);
    }

    static CommandWrapper skipStage(Long leadId, Long stageInstanceId, String json) {
        return command("SKIP", ENTITY_LEAD_STAGE, leadId, stageInstanceId, "/v1/leads/" + leadId + "/stages/" + stageInstanceId, json,
                null);
    }

    static CommandWrapper createLeadProcess(String json) {
        return command("CONFIGURE", ENTITY_LEAD_PROCESS, null, null, "/v1/leadprocesses", json, null);
    }

    static CommandWrapper createLeadProcessVersion(Long processDefinitionId, String json) {
        return command("CONFIGURE", ENTITY_LEAD_PROCESS, processDefinitionId, null,
                "/v1/leadprocesses/" + processDefinitionId + "/versions", json, null);
    }

    static CommandWrapper configureLeadProcessStages(Long processDefinitionId, Long versionId, String json) {
        return command("CONFIGURE", ENTITY_LEAD_PROCESS, processDefinitionId, versionId,
                "/v1/leadprocesses/" + processDefinitionId + "/versions/" + versionId + "/stages", json, null);
    }

    static CommandWrapper publishLeadProcess(Long processDefinitionId, Long versionId, String json) {
        return command("PUBLISH", ENTITY_LEAD_PROCESS, processDefinitionId, versionId,
                "/v1/leadprocesses/" + processDefinitionId + "/versions/" + versionId + "/publish", json, null);
    }

    private static CommandWrapper command(String action, String entity, Long entityId, Long subentityId, String href, String json,
            String idempotencyKey) {
        return new CommandWrapper(null, null, null, null, null, action, entity, entityId, subentityId, href, json, null, null, null, null,
                null, null, idempotencyKey, null, Set.of());
    }
}
