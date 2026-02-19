package com.squad.agent.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolResult;
import com.squad.llm.tool.builtin.BashExecToolCommand;
import com.squad.llm.tool.builtin.BuiltInToolCommand;
import com.squad.llm.tool.builtin.BuiltInToolExecutor;
import com.squad.llm.tool.builtin.BuiltInToolRegistry;
import com.squad.llm.tool.builtin.FileReadToolCommand;
import com.squad.llm.tool.builtin.FileSearchToolCommand;
import com.squad.llm.tool.builtin.FileWriteToolCommand;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Agent 컨테이너 내부에서 Built-in Tool을 실행하는 CLI 진입점.
 */
public class AgentToolCliApplication {

    public static void main(String[] args) {
        try {
            if (args.length < 1 || args[0].isBlank()) {
                throw new IllegalArgumentException("tool payload 인자가 필요합니다.");
            }

            String json = new String(Base64.getDecoder().decode(args[0]), StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);

            LlmToolCall call = new LlmToolCall(
                    String.valueOf(payload.get("id")),
                    String.valueOf(payload.get("name")),
                    payload.get("arguments") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of()
            );

            List<BuiltInToolCommand> commands = List.of(
                    new FileReadToolCommand(),
                    new FileWriteToolCommand(),
                    new FileSearchToolCommand(),
                    new BashExecToolCommand()
            );
            BuiltInToolExecutor executor = new BuiltInToolExecutor(
                    new BuiltInToolRegistry(commands),
                    commands,
                    "/workspace"
            );

            LlmToolResult result = executor.execute(call);
            System.out.print(result.output());
        } catch (Exception e) {
            System.err.print("[오류] " + e.getMessage());
            System.exit(1);
        }
    }
}
