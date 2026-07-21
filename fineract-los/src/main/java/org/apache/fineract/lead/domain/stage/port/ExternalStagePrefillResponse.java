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
package org.apache.fineract.lead.domain.stage.port;

import java.util.Map;

public record ExternalStagePrefillResponse(boolean successful, boolean idempotentReplay, String providerReference,
        Map<String, Object> normalizedValues, String errorCode, String message) {

    public ExternalStagePrefillResponse {
        normalizedValues = normalizedValues == null ? Map.of() : Map.copyOf(normalizedValues);
    }

    public static ExternalStagePrefillResponse success(String providerReference, Map<String, Object> normalizedValues) {
        return new ExternalStagePrefillResponse(true, false, providerReference, normalizedValues, null, "Prefill completed");
    }

    public static ExternalStagePrefillResponse replay(String providerReference, Map<String, Object> normalizedValues) {
        return new ExternalStagePrefillResponse(true, true, providerReference, normalizedValues, null, "Prefill already completed");
    }

    public static ExternalStagePrefillResponse failure(String errorCode, String message) {
        return new ExternalStagePrefillResponse(false, false, null, Map.of(), errorCode, message);
    }
}
