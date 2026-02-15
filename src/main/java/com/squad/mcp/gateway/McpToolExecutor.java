package com.squad.mcp.gateway;

import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import com.squad.mcp.client.McpClient;
import com.squad.mcp.client.McpToolCallResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MCP Gateway를 경유하여 도구를 실행하는 {@link LlmToolExecutor} 구현체.
 *
 * <p>LLM의 tool_use 응답에서 받은 alias를 {@link McpToolRegistry}를 통해
 * 올바른 MCP 서버와 원본 도구 이름으로 라우팅한 뒤, {@link McpClient}로
 * 실제 도구를 호출한다.</p>
 *
 * <h3>실행 흐름:</h3>
 * <ol>
 *   <li>toolCall.name() (alias) → {@link McpToolRegistry#findRoute(String)} → {@link ToolRoute}</li>
 *   <li>ToolRoute.mcpName() → {@link McpToolRegistry#getClient(String)} → {@link McpClient}</li>
 *   <li>{@link McpClient#callTool(String, java.util.Map)} → {@link McpToolCallResult}</li>
 *   <li>{@link McpToolCallResult} → {@link LlmToolResult} 변환</li>
 * </ol>
 *
 * <h3>에러 처리:</h3>
 * <p>라우트 미발견, 클라이언트 미발견, MCP 호출 실패 등은 예외를 던지지 않고
 * 에러 메시지를 포함한 {@link LlmToolResult}를 반환한다. 이를 통해 LLM이
 * 에러를 인식하고 다른 접근을 시도할 수 있도록 한다.</p>
 *
 * @see McpToolRegistry
 * @see LlmToolExecutor
 */
@Slf4j
@Component
public class McpToolExecutor implements LlmToolExecutor {

    private final McpToolRegistry mcpToolRegistry;

    /**
     * {@code McpToolExecutor}를 생성한다.
     *
     * @param mcpToolRegistry MCP 도구 레지스트리
     */
    public McpToolExecutor(McpToolRegistry mcpToolRegistry) {
        this.mcpToolRegistry = mcpToolRegistry;
    }

    /**
     * LLM의 tool_use 요청을 MCP 서버 경유로 실행한다.
     *
     * @param toolCall LLM이 요청한 도구 호출 정보
     * @return 도구 실행 결과
     */
    @Override
    public LlmToolResult execute(LlmToolCall toolCall) {
        Objects.requireNonNull(toolCall, "toolCall은 null일 수 없습니다");

        String alias = toolCall.name();
        log.debug("MCP 도구 실행 요청: alias={}, callId={}", alias, toolCall.id());

        ToolRoute route = mcpToolRegistry.findRoute(alias).orElse(null);
        if (route == null) {
            log.warn("도구 라우트를 찾을 수 없습니다: alias={}", alias);
            return errorResult(toolCall, "도구를 찾을 수 없습니다: " + alias);
        }

        McpClient client = mcpToolRegistry.getClient(route.mcpName()).orElse(null);
        if (client == null) {
            log.warn("MCP 클라이언트를 찾을 수 없습니다: mcpName={}", route.mcpName());
            return errorResult(toolCall, "MCP 서버에 연결할 수 없습니다: " + route.mcpName());
        }

        try {
            McpToolCallResult mcpResult = client.callTool(route.originalToolName(), toolCall.arguments());
            String output = formatMcpResult(mcpResult);

            log.debug("MCP 도구 실행 완료: alias={}, isError={}, outputLength={}",
                    alias, mcpResult.isError(), output.length());

            return new LlmToolResult(toolCall.id(), toolCall.name(), output);
        } catch (Exception e) {
            log.error("MCP 도구 실행 실패: alias={}, mcpName={}, toolName={}",
                    alias, route.mcpName(), route.originalToolName(), e);
            return errorResult(toolCall, "도구 실행 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private LlmToolResult errorResult(LlmToolCall toolCall, String errorMessage) {
        return new LlmToolResult(toolCall.id(), toolCall.name(), "[오류] " + errorMessage);
    }

    private String formatMcpResult(McpToolCallResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return result.isError() ? "[오류] 도구 실행 결과가 비어있습니다." : "";
        }

        String output = result.content().stream()
                .filter(c -> c.text() != null)
                .map(McpToolCallResult.Content::text)
                .collect(Collectors.joining("\n"));

        if (result.isError()) {
            return "[오류] " + output;
        }
        return output;
    }
}
