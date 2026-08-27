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

import org.springframework.util.StringUtils;

/**
 * Enum representation of app user type states.
 */
public enum AppUserType {

    INVALID(0, "appUserType.invalid"), //
    ADMIN(100, "appUserType.admin"), //
    STAFF(200, "appUserType.staff"), //
    AGENT(300, "appUserType.agent"); //

    private final Integer value;
    private final String code;

    public static AppUserType fromInt(final Integer typeValue) {

        return switch (typeValue) {
            case 100 -> AppUserType.ADMIN;
            case 200 -> AppUserType.STAFF;
            case 300 -> AppUserType.AGENT;
            default -> AppUserType.INVALID;
        };

    }

    public static AppUserType fromString(final String appUserTypeString) {

        AppUserType appUserType = AppUserType.INVALID;

        if (!StringUtils.hasLength(appUserTypeString)) {
            return appUserType;
        }

        if (appUserTypeString.equalsIgnoreCase(AppUserType.AGENT.toString())) {
            appUserType = AppUserType.AGENT;
        } else if (appUserTypeString.equalsIgnoreCase(AppUserType.STAFF.toString())) {
            appUserType = AppUserType.STAFF;
        } else if (appUserTypeString.equalsIgnoreCase(AppUserType.ADMIN.toString())) {
            appUserType = AppUserType.ADMIN;
        }

        return appUserType;
    }

    AppUserType(final Integer value, final String code) {
        this.value = value;
        this.code = code;
    }

    public boolean hasStateOf(final AppUserType state) {
        return this.value.equals(state.getValue());
    }

    public Integer getValue() {
        return this.value;
    }

    public String getCode() {
        return this.code;
    }

    public boolean isAgent() {
        return this.value.equals(AppUserType.AGENT.getValue());
    }

    public boolean isStaff() {
        return this.value.equals(AppUserType.STAFF.getValue());
    }

    public boolean isAdmin() {
        return this.value.equals(AppUserType.ADMIN.getValue());
    }
}