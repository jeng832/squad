package com.squad.agent.runner;

import java.util.List;
import java.util.Map;

/**
 * Agent Runner가 참조하는 에이전트 설정.
 */
public record AgentConfig(
        String id,
        String name,
        String roleType,
        String role,
        Map<String, Object> llmConfig,
        List<String> mcps,
        List<String> skills
) {

    public AgentConfig withId(String newId) {
        return new AgentConfig(newId, name, roleType, role, llmConfig, mcps, skills);
    }
}
