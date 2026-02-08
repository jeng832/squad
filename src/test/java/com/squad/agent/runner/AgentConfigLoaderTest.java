package com.squad.agent.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentConfigLoaderTest {

    @Test
    void json을_파싱해_설정_생성() {
        String json = """
                {
                  "id": "agent-1",
                  "name": "Agent",
                  "roleType": "worker",
                  "role": "do things",
                  "llmConfig": {"provider": "claude"},
                  "mcps": ["github"],
                  "skills": ["summarize"]
                }
                """;

        AgentConfigLoader loader = new AgentConfigLoader(new ObjectMapper());
        AgentConfig config = loader.load(json, null);

        assertThat(config.id()).isEqualTo("agent-1");
        assertThat(config.name()).isEqualTo("Agent");
        assertThat(config.roleType()).isEqualTo("worker");
        assertThat(config.llmConfig()).containsEntry("provider", "claude");
    }

    @Test
    void AGENT_ID로_id를_덮어쓴다() {
        String json = """
                {"id": "agent-1", "name": "Agent", "roleType": "worker", "role": "do"}
                """;

        AgentConfigLoader loader = new AgentConfigLoader(new ObjectMapper());
        AgentConfig config = loader.load(json, "agent-override");

        assertThat(config.id()).isEqualTo("agent-override");
    }
}
