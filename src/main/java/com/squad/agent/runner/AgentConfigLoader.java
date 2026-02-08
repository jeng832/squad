package com.squad.agent.runner;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * 환경변수의 AGENT_CONFIG JSON을 읽어 AgentConfig로 파싱합니다.
 */
public class AgentConfigLoader {

    private final ObjectMapper objectMapper;

    public AgentConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentConfig loadFromEnv() {
        String json = System.getenv("AGENT_CONFIG");
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("AGENT_CONFIG 환경변수가 비어 있습니다.");
        }
        String overrideId = System.getenv("AGENT_ID");
        return load(json, overrideId);
    }

    public AgentConfig load(String json, String overrideId) {
        try {
            AgentConfig config = objectMapper.readValue(json, AgentConfig.class);
            if (overrideId != null && !overrideId.isBlank()) {
                config = config.withId(overrideId);
            }
            if (config.id() == null || config.id().isBlank()) {
                throw new IllegalStateException("AgentConfig.id가 비어 있습니다.");
            }
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("AGENT_CONFIG 파싱 실패", e);
        }
    }
}
