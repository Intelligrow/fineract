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
package org.apache.fineract.organisation.agentcollection.data;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
@Builder
public class AgentSettlementSearchParameters {

    Long agentId;
    Long officeId;
    LocalDate fromDate;
    LocalDate toDate;
    String status;
    Integer offset;
    Integer limit;
    String orderBy;
    String sortOrder;

    public boolean hasAgentId() {
        return this.agentId != null && this.agentId > 0;
    }

    public boolean hasOfficeId() {
        return this.officeId != null && this.officeId > 0;
    }

    public boolean hasFromDate() {
        return this.fromDate != null;
    }

    public boolean hasToDate() {
        return this.toDate != null;
    }

    public boolean hasLimit() {
        return this.limit != null && this.limit > 0;
    }

    public boolean hasOffset() {
        return this.offset != null;
    }

    public boolean hasOrderBy() {
        return StringUtils.isNotBlank(this.orderBy);
    }

    public boolean hasSortOrder() {
        return StringUtils.isNotBlank(this.sortOrder);
    }
}
