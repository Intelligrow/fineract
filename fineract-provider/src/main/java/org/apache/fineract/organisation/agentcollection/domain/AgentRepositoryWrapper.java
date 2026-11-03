/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.organisation.agentcollection.domain;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.agentcollection.exception.AgentNotFoundException;
import org.apache.fineract.organisation.agentcollection.exception.InActiveAgentException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRepositoryWrapper {

	private final AgentRepository repository;

	public Agent findOneWithNotFoundDetection(final Long agentId) {
		return this.repository.findByIdWithLimits(agentId).orElseThrow(() -> new AgentNotFoundException(agentId));
	}

	public Agent findOneWithNotFoundDetectionLocked(final Long agentId) {
		return this.repository.findByIdWithLimitsLocked(agentId).orElseThrow(() -> new AgentNotFoundException(agentId));
	}

	public Optional<Agent> findActiveAgentByAppUserId(final Long appUserId) {
		return this.repository.findActiveAgentByAppUserId(appUserId);
	}

	public Optional<Agent> findAgentByAppUserIdWithStatusCheck(final Long appUserId) {
		return this.repository.findAgentByAppUserId(appUserId).map(agent -> {
			if (agent.status().isInactive()){
				throw new InActiveAgentException(agent.getId());
			}else{
				return agent;
			}
		});
	}

	public Optional<Agent> findAgentByAppUserIdLocked(final Long appUserId) {
		return this.repository.findAgentByAppUserIdLocked(appUserId);
	}

	public boolean existsByAppUserId(final Long appUserId) {
		return this.repository.existsByAppUser_Id(appUserId);
	}

	public boolean existsByAppUserIdAndIdNot(final Long appUserId, final Long agentId) {
		return this.repository.existsByAppUser_IdAndIdNot(appUserId, agentId);
	}

	public boolean existsByStaffId(final Long staffId) {
		return this.repository.existsByStaff_Id(staffId);
	}

	public boolean existsByStaffIdAndIdNot(final Long staffId, final Long agentId) {
		return this.repository.existsByStaff_IdAndIdNot(staffId, agentId);
	}

	public Agent saveAndFlush(final Agent agent) {
		return this.repository.saveAndFlush(agent);
	}
}
