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
package org.apache.fineract.organisation.agentcollection.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRepository extends JpaRepository<Agent, Long>, JpaSpecificationExecutor<Agent> {

    @Query("select distinct agent from Agent agent left join fetch agent.transactionLimits where agent.id = :agentId")
    Optional<Agent> findByIdWithLimits(@Param("agentId") Long agentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct agent from Agent agent left join fetch agent.transactionLimits where agent.id = :agentId")
    Optional<Agent> findByIdWithLimitsLocked(@Param("agentId") Long agentId);

    @Query("""
            select distinct agent from Agent agent
            left join fetch agent.transactionLimits
            where agent.appUser.id = :appUserId and agent.status = 300
            """)
    Optional<Agent> findActiveAgentByAppUserId(@Param("appUserId") Long appUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct agent from Agent agent
            left join fetch agent.transactionLimits
            where agent.appUser.id = :appUserId and agent.status = 300
            """)
    Optional<Agent> findActiveAgentByAppUserIdLocked(@Param("appUserId") Long appUserId);

    boolean existsByAppUser_Id(Long appUserId);

    boolean existsByAppUser_IdAndIdNot(Long appUserId, Long id);

    boolean existsByStaff_Id(Long staffId);

    boolean existsByStaff_IdAndIdNot(Long staffId, Long id);
}
