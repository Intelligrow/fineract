package org.apache.fineract.organisation.agentcollection.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.AgentLookupPlatformService;
import org.apache.fineract.organisation.agentcollection.domain.Agent;
import org.apache.fineract.organisation.agentcollection.domain.AgentRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentLookupPlatformServiceImpl implements AgentLookupPlatformService {

    private final AgentRepository agentRepository;

    @Override
    public boolean isAgent(Long appUserId) {
        return agentRepository.findActiveAgentByAppUserId(appUserId).isPresent();
    }

    @Override
    public Long findAgentIdByAppUserId(Long appUserId) {
        return agentRepository.findActiveAgentByAppUserId(appUserId).map(Agent::getId).orElse(null);
    }
}