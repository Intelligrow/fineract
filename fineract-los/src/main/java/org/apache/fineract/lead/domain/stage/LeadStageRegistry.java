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
package org.apache.fineract.lead.domain.stage;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.lead.exception.LeadStageHandlerNotFoundException;
import org.springframework.stereotype.Component;

@Component
public final class LeadStageRegistry {

    private final Map<String, LeadStageHandler> handlers;

    public LeadStageRegistry(List<LeadStageHandler> discovered) {
        final Map<String, LeadStageHandler> mapped = new HashMap<>();
        for (LeadStageHandler handler : discovered) {
            final String stageCode = normalizeForRegistration(handler.stageCode());
            if (mapped.put(stageCode, handler) != null) {
                throw new IllegalStateException("Duplicate stage code: " + stageCode);
            }
        }
        this.handlers = Map.copyOf(mapped);
    }

    public LeadStageHandler getRequired(String stageCode) {
        final String normalized = normalizeForLookup(stageCode);
        final LeadStageHandler handler = handlers.get(normalized);
        if (handler == null) {
            throw new LeadStageHandlerNotFoundException(normalized);
        }
        return handler;
    }

    public boolean contains(String stageCode) {
        return handlers.containsKey(normalizeForLookup(stageCode));
    }

    public Set<String> stageCodes() {
        return handlers.keySet();
    }

    private static String normalizeForRegistration(String stageCode) {
        if (StringUtils.isBlank(stageCode)) {
            throw new IllegalStateException("Stage code must not be blank");
        }
        return stageCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeForLookup(String stageCode) {
        if (StringUtils.isBlank(stageCode)) {
            throw new LeadStageHandlerNotFoundException(stageCode);
        }
        return stageCode.trim().toUpperCase(Locale.ROOT);
    }
}
