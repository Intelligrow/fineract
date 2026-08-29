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
package org.apache.fineract.useradministration.domain;

import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

/**
 * Utility to convert {@link AppUserType} enum values into {@link EnumOptionData} for use in dropdown/template
 * responses.
 */
public final class AppUserEnumerations {

    private AppUserEnumerations() {

    }

    public static EnumOptionData appUserType(final Integer statusId) {
        return appUserType(AppUserType.fromInt(statusId));
    }

    public static EnumOptionData appUserType(final AppUserType type) {
        EnumOptionData optionData = new EnumOptionData(AppUserType.INVALID.getValue().longValue(), AppUserType.INVALID.getCode(),
                "Invalid");
        switch (type) {
            case AppUserType.AGENT:
                optionData = new EnumOptionData(AppUserType.AGENT.getValue().longValue(), AppUserType.AGENT.getCode(), "Agent");
            break;
            case AppUserType.STAFF:
                optionData = new EnumOptionData(AppUserType.STAFF.getValue().longValue(), AppUserType.STAFF.getCode(), "Staff");
            break;
            case AppUserType.ADMIN:
                optionData = new EnumOptionData(AppUserType.ADMIN.getValue().longValue(), AppUserType.ADMIN.getCode(), "Admin");
            break;
            case AppUserType.INVALID:
            break;
        }
        return optionData;
    }

    public static List<EnumOptionData> appUserTypeOptions() {
        final List<EnumOptionData> allowedTypeOptions = new ArrayList<>();
        for (final AppUserType type : AppUserType.values()) {
            if (type.isAgent() || type.isStaff() || type.isAdmin()) {
                allowedTypeOptions.add(appUserType(type));
            }
        }
        return allowedTypeOptions;
    }
}