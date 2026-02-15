package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import com.squad.mcp.gateway.McpToolExecutor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Built-in Tool과 MCP Tool 실행을 통합 라우팅하는 Executor.
 */
@Component
@Primary
public class CompositeToolExecutor implements LlmToolExecutor {

    private final BuiltInToolExecutor builtInToolExecutor;
    private final McpToolExecutor mcpToolExecutor;

    public CompositeToolExecutor(BuiltInToolExecutor builtInToolExecutor, McpToolExecutor mcpToolExecutor) {
        this.builtInToolExecutor = builtInToolExecutor;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    @Override
    public LlmToolResult execute(LlmToolCall toolCall) {
        if (builtInToolExecutor.supports(toolCall.name())) {
            return builtInToolExecutor.execute(toolCall);
        }
        return mcpToolExecutor.execute(toolCall);
    }
}
