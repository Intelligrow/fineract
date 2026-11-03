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
package org.apache.fineract.organisation.agentcollection.starter;

import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.SqlValidator;
import org.apache.fineract.organisation.agentcollection.domain.AgentCollectionTransactionRepository;
import org.apache.fineract.organisation.agentcollection.domain.AgentRepositoryWrapper;
import org.apache.fineract.organisation.agentcollection.domain.AgentSettlementRepository;
import org.apache.fineract.organisation.agentcollection.serialization.AgentCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionReadPlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionReadPlatformServiceImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionTemplateService;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionTemplateServiceImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionTransactionValidationService;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionTransactionValidationServiceImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionWritePlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentCollectionWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentReadPlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentReadPlatformServiceImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentSettlementReadPlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentSettlementReadPlatformServiceImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentSettlementValidationService;
import org.apache.fineract.organisation.agentcollection.service.AgentSettlementValidationServiceImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentSettlementWritePlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentSettlementWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentValidationService;
import org.apache.fineract.organisation.agentcollection.service.AgentValidationServiceImpl;
import org.apache.fineract.organisation.agentcollection.service.AgentWritePlatformService;
import org.apache.fineract.organisation.agentcollection.service.AgentWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.organisation.staff.service.StaffReadService;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepository;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadService;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OrganisationAgentCollectionConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentReadPlatformService.class)
    public AgentReadPlatformService agentReadPlatformService(final JdbcTemplate jdbcTemplate, final PlatformSecurityContext context,
            final PaginationHelper paginationHelper, final DatabaseSpecificSQLGenerator sqlGenerator, final SqlValidator sqlValidator,
            final OfficeReadPlatformService officeReadPlatformService, final StaffReadService staffReadService,
            final CurrencyReadPlatformService currencyReadPlatformService, final PaymentTypeReadService paymentTypeReadService) {
        return new AgentReadPlatformServiceImpl(jdbcTemplate, context, paginationHelper, sqlGenerator, sqlValidator,
                officeReadPlatformService, staffReadService, currencyReadPlatformService, paymentTypeReadService);
    }

    @Bean
    @ConditionalOnMissingBean(AgentValidationService.class)
    public AgentValidationService agentValidationService(final PlatformSecurityContext context,
            final AgentRepositoryWrapper agentRepositoryWrapper, final OfficeRepositoryWrapper officeRepositoryWrapper) {
        return new AgentValidationServiceImpl(context, agentRepositoryWrapper, officeRepositoryWrapper);
    }

    @Bean
    @ConditionalOnMissingBean(AgentCollectionReadPlatformService.class)
    public AgentCollectionReadPlatformService agentCollectionReadPlatformService(final JdbcTemplate jdbcTemplate,
            final PlatformSecurityContext context, final AgentReadPlatformService agentReadPlatformService,
            final PaginationHelper paginationHelper, final DatabaseSpecificSQLGenerator sqlGenerator, final SqlValidator sqlValidator) {
        return new AgentCollectionReadPlatformServiceImpl(jdbcTemplate, context, agentReadPlatformService, paginationHelper, sqlGenerator,
                sqlValidator);
    }

    @Bean
    @ConditionalOnMissingBean(AgentCollectionTemplateService.class)
    public AgentCollectionTemplateService agentCollectionTemplateService(final AgentValidationService agentValidationService,
            final AgentCollectionReadPlatformService agentCollectionReadPlatformService) {
        return new AgentCollectionTemplateServiceImpl(agentValidationService, agentCollectionReadPlatformService);
    }

    @Bean
    @ConditionalOnMissingBean(AgentCollectionTransactionValidationService.class)
    public AgentCollectionTransactionValidationService agentCollectionTransactionValidationService(
            final AgentValidationService agentValidationService,
            final AgentCollectionReadPlatformService agentCollectionReadPlatformService,
            final AgentCollectionTransactionRepository agentCollectionTransactionRepository) {
        return new AgentCollectionTransactionValidationServiceImpl(agentValidationService, agentCollectionReadPlatformService,
                agentCollectionTransactionRepository);
    }

    @Bean
    @ConditionalOnMissingBean(AgentCollectionWritePlatformService.class)
    public AgentCollectionWritePlatformService agentCollectionWritePlatformService(final AgentValidationService agentValidationService,
            final AgentCollectionTransactionValidationService agentCollectionTransactionValidationService,
            final AgentCollectionTransactionRepository agentCollectionTransactionRepository) {
        return new AgentCollectionWritePlatformServiceJpaRepositoryImpl(agentValidationService, agentCollectionTransactionValidationService,
                agentCollectionTransactionRepository);
    }

    @Bean
    @ConditionalOnMissingBean(AgentSettlementReadPlatformService.class)
    public AgentSettlementReadPlatformService agentSettlementReadPlatformService(final JdbcTemplate jdbcTemplate,
            final PlatformSecurityContext context, final AgentCollectionReadPlatformService agentCollectionReadPlatformService,
            final PaginationHelper paginationHelper, final DatabaseSpecificSQLGenerator sqlGenerator, final SqlValidator sqlValidator) {
        return new AgentSettlementReadPlatformServiceImpl(jdbcTemplate, context, agentCollectionReadPlatformService, paginationHelper,
                sqlGenerator, sqlValidator);
    }

    @Bean
    @ConditionalOnMissingBean(AgentSettlementValidationService.class)
    public AgentSettlementValidationService agentSettlementValidationService(final AgentValidationService agentValidationService) {
        return new AgentSettlementValidationServiceImpl(agentValidationService);
    }

    @Bean
    @ConditionalOnMissingBean(AgentSettlementWritePlatformService.class)
    public AgentSettlementWritePlatformService agentSettlementWritePlatformService(final PlatformSecurityContext context,
            final AgentValidationService agentValidationService, final AgentSettlementValidationService agentSettlementValidationService,
            final AgentSettlementRepository agentSettlementRepository,
            final AgentCollectionTransactionRepository agentCollectionTransactionRepository, final JournalEntryWritePlatformService journalEntryWritePlatformService) {
        return new AgentSettlementWritePlatformServiceJpaRepositoryImpl(context, agentValidationService, agentSettlementValidationService,
                agentSettlementRepository, agentCollectionTransactionRepository,journalEntryWritePlatformService);
    }

    @Bean
    @ConditionalOnMissingBean(AgentWritePlatformService.class)
    public AgentWritePlatformService agentWritePlatformService(final PlatformSecurityContext context,
            final AgentCommandFromApiJsonDeserializer fromApiJsonDeserializer, final AgentRepositoryWrapper agentRepositoryWrapper,
            final AgentValidationService agentValidationService, final AppUserRepository appUserRepository,
            final StaffRepositoryWrapper staffRepositoryWrapper, final PaymentTypeRepository paymentTypeRepository,
            final CurrencyReadPlatformService currencyReadPlatformService) {
        return new AgentWritePlatformServiceJpaRepositoryImpl(context, fromApiJsonDeserializer, agentRepositoryWrapper,
                agentValidationService, appUserRepository, staffRepositoryWrapper, paymentTypeRepository, currencyReadPlatformService);
    }
}
