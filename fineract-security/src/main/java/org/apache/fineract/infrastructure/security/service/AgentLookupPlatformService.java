package org.apache.fineract.infrastructure.security.service;

public interface AgentLookupPlatformService {

    boolean isAgent(Long appUserId);

    Long findAgentIdByAppUserId(Long appUserId);
}