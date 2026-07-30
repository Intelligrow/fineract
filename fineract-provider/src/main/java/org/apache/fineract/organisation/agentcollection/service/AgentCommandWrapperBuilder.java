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

import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_ACTIVATE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_CREATE;
import static org.apache.fineract.commands.domain.CommandWrapperConstants.ACTION_UPDATE;
import static org.apache.fineract.organisation.agentcollection.api.AgentApiConstants.AGENT_ENTITY_NAME;

import org.apache.fineract.commands.domain.CommandWrapper;

public class AgentCommandWrapperBuilder {

    private static final String ACTION_DEACTIVATE = "DEACTIVATE";

    public CommandWrapper createAgent(final String json) {
        return build(ACTION_CREATE, null, "/agents/template", json);
    }

    public CommandWrapper updateAgent(final Long agentId, final String json) {
        return build(ACTION_UPDATE, agentId, "/agents/" + agentId, json);
    }

    public CommandWrapper activateAgent(final Long agentId, final String json) {
        return build(ACTION_ACTIVATE, agentId, "/agents/" + agentId + "?command=activate", json);
    }

    public CommandWrapper deactivateAgent(final Long agentId, final String json) {
        return build(ACTION_DEACTIVATE, agentId, "/agents/" + agentId + "?command=deactivate", json);
    }

    private CommandWrapper build(final String actionName, final Long entityId, final String href, final String json) {
        return new CommandWrapper(null, null, null, null, null, actionName, AGENT_ENTITY_NAME, entityId, null, href, json, null, null, null,
                null, null, null, null, null, null);
    }
}
