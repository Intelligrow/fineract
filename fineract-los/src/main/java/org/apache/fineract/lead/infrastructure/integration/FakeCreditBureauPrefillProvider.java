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
package org.apache.fineract.lead.infrastructure.integration;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.lead.domain.stage.port.ExternalStagePrefillPort;
import org.apache.fineract.lead.domain.stage.port.ExternalStagePrefillRequest;
import org.apache.fineract.lead.domain.stage.port.ExternalStagePrefillResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "fineract.module.lead.enabled", havingValue = "true")
public class FakeCreditBureauPrefillProvider implements ExternalStagePrefillPort {

    public static final String SIMULATE_FAILURE_ATTRIBUTE = "simulateCreditBureauFailure";

    private final Map<String, ExternalStagePrefillResponse> completedByIdempotencyKey = new ConcurrentHashMap<>();
    private final AtomicInteger fakeExecutions = new AtomicInteger();

    @Override
    public ExternalStagePrefillResponse requestPrefill(ExternalStagePrefillRequest request) {
        if (request == null || StringUtils.isBlank(request.idempotencyKey())) {
            return ExternalStagePrefillResponse.failure("lead.creditbureau.idempotency.required",
                    "Idempotency key is required before requesting credit bureau prefill.");
        }
        final ExternalStagePrefillResponse existing = completedByIdempotencyKey.get(request.idempotencyKey());
        if (existing != null) {
            return ExternalStagePrefillResponse.replay(existing.providerReference(), existing.normalizedValues());
        }
        if (Boolean.TRUE.equals(request.context().attributes().get(SIMULATE_FAILURE_ATTRIBUTE))) {
            return ExternalStagePrefillResponse.failure("lead.creditbureau.fake.failure", "Fake credit bureau prefill failed.");
        }

        fakeExecutions.incrementAndGet();
        final Map<String, Object> normalizedValues = Map.of("bureauScore", 742, "riskBand", "LOW", "reportDate",
                LocalDate.of(2026, 1, 1).toString(), "source", "FAKE_CREDIT_BUREAU");
        final ExternalStagePrefillResponse response = ExternalStagePrefillResponse.success("FAKE-CB-" + request.context().stageInstanceId(),
                normalizedValues);
        completedByIdempotencyKey.put(request.idempotencyKey(), response);
        return response;
    }

    public int fakeExecutionCount() {
        return fakeExecutions.get();
    }

    public int realExternalRequestCount() {
        return 0;
    }
}
