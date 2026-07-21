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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record LeadStageValidationResult(List<LeadStageValidationError> errors) {

    public LeadStageValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static LeadStageValidationResult valid() {
        return new LeadStageValidationResult(List.of());
    }

    public static LeadStageValidationResult invalid(String code, String message) {
        return invalid(code, message, true);
    }

    public static LeadStageValidationResult invalid(String code, String message, boolean correctable) {
        return new LeadStageValidationResult(List.of(new LeadStageValidationError(code, message, correctable)));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public LeadStageValidationResult merge(LeadStageValidationResult other) {
        return merge(List.of(this, other));
    }

    public static LeadStageValidationResult merge(LeadStageValidationResult... results) {
        return merge(List.of(results));
    }

    public static LeadStageValidationResult merge(Collection<LeadStageValidationResult> results) {
        final List<LeadStageValidationError> merged = new ArrayList<>();
        for (LeadStageValidationResult result : results) {
            if (result != null) {
                merged.addAll(result.errors());
            }
        }
        return new LeadStageValidationResult(merged);
    }
}
