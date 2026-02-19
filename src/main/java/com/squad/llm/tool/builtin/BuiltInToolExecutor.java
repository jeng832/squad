package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent Runtime 내장 도구 실행기.
 */
@Slf4j
@Component
public class BuiltInToolExecutor implements LlmToolExecutor {

    private final BuiltInToolRegistry toolRegistry;
    private final BuiltInToolContext context;
    private final Map<String, BuiltInToolCommand> commandMap;

    public BuiltInToolExecutor(
            BuiltInToolRegistry toolRegistry,
            List<BuiltInToolCommand> commands,
            @Value("${squad.builtin-tools.workspace-root:/tmp/squad-workspace}") String workspaceRoot
    ) {
        this.toolRegistry = toolRegistry;
        this.context = new BuiltInToolContext(Path.of(workspaceRoot).toAbsolutePath().normalize());
        this.commandMap = commands.stream()
                .collect(Collectors.toUnmodifiableMap(BuiltInToolCommand::toolName, Function.identity()));
    }

    @Override
    public LlmToolResult execute(LlmToolCall toolCall) {
        Objects.requireNonNull(toolCall, "toolCall은 null일 수 없습니다");

        if (!toolRegistry.isBuiltInTool(toolCall.name())) {
            return error(toolCall, "지원하지 않는 Built-in Tool입니다: " + toolCall.name());
        }

        BuiltInToolCommand command = commandMap.get(toolCall.name());
        if (command == null) {
            return error(toolCall, "Built-in Tool 구현체를 찾을 수 없습니다: " + toolCall.name());
        }

        try {
            String output = command.execute(toolCall, context);
            return ok(toolCall, output);
        } catch (Exception e) {
            log.warn("Built-in Tool 실행 실패: tool={}, callId={}", toolCall.name(), toolCall.id(), e);
            return error(toolCall, e.getMessage());
        }
    }

    public boolean supports(String toolName) {
        return toolRegistry.isBuiltInTool(toolName);
    }

    private LlmToolResult ok(LlmToolCall call, String output) {
        return new LlmToolResult(call.id(), call.name(), output);
    }

    private LlmToolResult error(LlmToolCall call, String message) {
        return new LlmToolResult(call.id(), call.name(), "[오류] " + message);
    }
}
