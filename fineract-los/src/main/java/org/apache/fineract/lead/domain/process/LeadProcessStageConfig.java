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
package org.apache.fineract.lead.domain.process;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.lead.domain.stage.StageConfiguration;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_lead_process_stage_config")
public class LeadProcessStageConfig extends AbstractPersistableCustom<Long> {

    @Column(name = "stage_code", nullable = false, length = 100)
    private String stageCode;

    @Column(name = "sequence_no", nullable = false)
    private int sequence;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "skippable", nullable = false)
    private boolean skippable;

    @Column(name = "maker_checker_required", nullable = false)
    private boolean makerCheckerRequired;

    @Column(name = "auto_prefill", nullable = false)
    private boolean autoPrefill;

    @Column(name = "manual_fallback", nullable = false)
    private boolean manualFallback;

    @Column(name = "assigned_role_id")
    private Long assignedRoleId;

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "required_datatables", length = 1000)
    private String requiredDatatables;

    @Column(name = "document_requirements_json", length = 2000)
    private String documentRequirementsJson;

    @Column(name = "options_json", length = 2000)
    private String optionsJson;

    public static LeadProcessStageConfig from(StageConfiguration configuration) {
        return create(configuration.stageCode(), configuration.sequence(), configuration.enabled(), configuration.mandatory(),
                configuration.skippable(), configuration.makerCheckerRequired(), configuration.autoPrefill(),
                configuration.manualFallback(), null, null, configuration.requiredDatatables(), null, null);
    }

    public static LeadProcessStageConfig create(String stageCode, int sequence, boolean enabled, boolean mandatory, boolean skippable,
            boolean makerCheckerRequired, boolean autoPrefill, boolean manualFallback, Long assignedRoleId, Integer slaHours,
            Set<String> requiredDatatables, String documentRequirementsJson, String optionsJson) {
        final LeadProcessStageConfig config = new LeadProcessStageConfig();
        config.stageCode = stageCode;
        config.sequence = sequence;
        config.enabled = enabled;
        config.mandatory = mandatory;
        config.skippable = skippable;
        config.makerCheckerRequired = makerCheckerRequired;
        config.autoPrefill = autoPrefill;
        config.manualFallback = manualFallback;
        config.assignedRoleId = assignedRoleId;
        config.slaHours = slaHours;
        config.requiredDatatables = requiredDatatables == null || requiredDatatables.isEmpty() ? null
                : requiredDatatables.stream().sorted().collect(Collectors.joining(","));
        config.documentRequirementsJson = documentRequirementsJson;
        config.optionsJson = optionsJson;
        return config;
    }

    public static LeadProcessStageConfig copyOf(LeadProcessStageConfig source) {
        return create(source.stageCode, source.sequence, source.enabled, source.mandatory, source.skippable, source.makerCheckerRequired,
                source.autoPrefill, source.manualFallback, source.assignedRoleId, source.slaHours,
                source.toStageConfiguration().requiredDatatables(), source.documentRequirementsJson, source.optionsJson);
    }

    public StageConfiguration toStageConfiguration() {
        final Set<String> datatables = StringUtils.isBlank(requiredDatatables) ? Set.of()
                : Arrays.stream(requiredDatatables.split(",")).map(String::trim).filter(StringUtils::isNotBlank)
                        .collect(Collectors.toUnmodifiableSet());
        return new StageConfiguration(stageCode, enabled, sequence, mandatory, skippable, makerCheckerRequired, datatables, Set.of(),
                autoPrefill, manualFallback, java.util.Map.of());
    }
}
