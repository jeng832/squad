package com.squad.agent.runner;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent Container 내부에서 실행되는 Runner 진입점.
 */
public class AgentRunnerApplication {

    public static void main(String[] args) {
        AgentConfigLoader loader = new AgentConfigLoader(new ObjectMapper());
        AgentConfig config = loader.loadFromEnv();

        System.out.println("[agent-runner] started id=" + config.id());
        // TODO: 메시지 수신/처리 루프는 5-3 이후 단계에서 구현
    }
}
