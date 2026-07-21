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
package org.apache.fineract.lead.data;

import org.apache.fineract.lead.domain.stage.LeadStageInstance;
import org.apache.fineract.lead.domain.stage.LeadStageStatus;

public record LeadStageData(Long id, Long processInstanceId, String stageCode, int sequence, boolean mandatory, boolean skippable,
        LeadStageStatus status, long revision, Long pendingCheckRevision, Long version) {

    public static LeadStageData from(LeadStageInstance stage) {
        return new LeadStageData(stage.getId(), stage.getProcessInstanceId(), stage.getStageCode(), stage.getSequence(),
                stage.isMandatory(), stage.isSkippable(), stage.getStatus(), stage.getRevision(), stage.getPendingCheckRevision(),
                stage.getVersion());
    }
}
