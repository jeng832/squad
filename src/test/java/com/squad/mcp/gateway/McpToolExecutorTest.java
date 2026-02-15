package com.squad.mcp.gateway;

import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolResult;
import com.squad.mcp.client.McpClient;
import com.squad.mcp.client.McpToolCallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpToolExecutor")
class McpToolExecutorTest {

    @Mock
    private McpToolRegistry mcpToolRegistry;

    @Mock
    private McpClient mcpClient;

    private McpToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new McpToolExecutor(mcpToolRegistry);
    }

    @Nested
    @DisplayName("execute")
    class ExecuteTest {

        @Test
        @DisplayName("정상적으로 MCP 도구를 실행하고 결과를 반환한다")
        void executesToolSuccessfully() {
            LlmToolCall toolCall = new LlmToolCall(
                    "call-1", "github__get_issue", Map.of("owner", "test", "repo", "squad"));

            ToolRoute route = ToolRoute.of("github", "get_issue");
            when(mcpToolRegistry.findRoute("github__get_issue")).thenReturn(Optional.of(route));
            when(mcpToolRegistry.getClient("github")).thenReturn(Optional.of(mcpClient));

            McpToolCallResult mcpResult = new McpToolCallResult(
                    List.of(new McpToolCallResult.Content("text", "Issue #1: 버그 수정")),
                    false
            );
            when(mcpClient.callTool("get_issue", Map.of("owner", "test", "repo", "squad")))
                    .thenReturn(mcpResult);

            LlmToolResult result = executor.execute(toolCall);

            assertThat(result.toolCallId()).isEqualTo("call-1");
            assertThat(result.name()).isEqualTo("github__get_issue");
            assertThat(result.output()).isEqualTo("Issue #1: 버그 수정");
        }

        @Test
        @DisplayName("여러 content를 줄바꿈으로 합쳐서 반환한다")
        void joinsMultipleContents() {
            LlmToolCall toolCall = new LlmToolCall("call-2", "github__search", Map.of("q", "test"));

            ToolRoute route = ToolRoute.of("github", "search");
            when(mcpToolRegistry.findRoute("github__search")).thenReturn(Optional.of(route));
            when(mcpToolRegistry.getClient("github")).thenReturn(Optional.of(mcpClient));

            McpToolCallResult mcpResult = new McpToolCallResult(
                    List.of(
                            new McpToolCallResult.Content("text", "결과 1"),
                            new McpToolCallResult.Content("text", "결과 2")
                    ),
                    false
            );
            when(mcpClient.callTool("search", Map.of("q", "test"))).thenReturn(mcpResult);

            LlmToolResult result = executor.execute(toolCall);

            assertThat(result.output()).isEqualTo("결과 1\n결과 2");
        }

        @Test
        @DisplayName("MCP 도구 실행 결과가 에러이면 [오류] 접두사를 붙인다")
        void returnsErrorPrefixForMcpError() {
            LlmToolCall toolCall = new LlmToolCall("call-3", "github__get_issue", Map.of());

            ToolRoute route = ToolRoute.of("github", "get_issue");
            when(mcpToolRegistry.findRoute("github__get_issue")).thenReturn(Optional.of(route));
            when(mcpToolRegistry.getClient("github")).thenReturn(Optional.of(mcpClient));

            McpToolCallResult mcpResult = new McpToolCallResult(
                    List.of(new McpToolCallResult.Content("text", "권한이 없습니다")),
                    true
            );
            when(mcpClient.callTool("get_issue", Map.of())).thenReturn(mcpResult);

            LlmToolResult result = executor.execute(toolCall);

            assertThat(result.output()).startsWith("[오류]");
            assertThat(result.output()).contains("권한이 없습니다");
        }

        @Test
        @DisplayName("빈 content로 에러인 경우 에러 메시지를 반환한다")
        void returnsErrorForEmptyContentWithError() {
            LlmToolCall toolCall = new LlmToolCall("call-4", "github__get_issue", Map.of());

            ToolRoute route = ToolRoute.of("github", "get_issue");
            when(mcpToolRegistry.findRoute("github__get_issue")).thenReturn(Optional.of(route));
            when(mcpToolRegistry.getClient("github")).thenReturn(Optional.of(mcpClient));

            McpToolCallResult mcpResult = new McpToolCallResult(List.of(), true);
            when(mcpClient.callTool("get_issue", Map.of())).thenReturn(mcpResult);

            LlmToolResult result = executor.execute(toolCall);

            assertThat(result.output()).startsWith("[오류]");
        }
    }

    @Nested
    @DisplayName("에러 처리")
    class ErrorHandlingTest {

        @Test
        @DisplayName("라우트를 찾을 수 없으면 에러 결과를 반환한다")
        void returnsErrorWhenRouteNotFound() {
            LlmToolCall toolCall = new LlmToolCall("call-5", "unknown__tool", Map.of());
            when(mcpToolRegistry.findRoute("unknown__tool")).thenReturn(Optional.empty());

            LlmToolResult result = executor.execute(toolCall);

            assertThat(result.toolCallId()).isEqualTo("call-5");
            assertThat(result.output()).contains("[오류]");
            assertThat(result.output()).contains("unknown__tool");
        }

        @Test
        @DisplayName("클라이언트를 찾을 수 없으면 에러 결과를 반환한다")
        void returnsErrorWhenClientNotFound() {
            LlmToolCall toolCall = new LlmToolCall("call-6", "github__get_issue", Map.of());

            ToolRoute route = ToolRoute.of("github", "get_issue");
            when(mcpToolRegistry.findRoute("github__get_issue")).thenReturn(Optional.of(route));
            when(mcpToolRegistry.getClient("github")).thenReturn(Optional.empty());

            LlmToolResult result = executor.execute(toolCall);

            assertThat(result.output()).contains("[오류]");
            assertThat(result.output()).contains("github");
        }

        @Test
        @DisplayName("MCP 호출 중 예외가 발생하면 에러 결과를 반환한다")
        void returnsErrorWhenMcpCallFails() {
            LlmToolCall toolCall = new LlmToolCall("call-7", "github__get_issue", Map.of());

            ToolRoute route = ToolRoute.of("github", "get_issue");
            when(mcpToolRegistry.findRoute("github__get_issue")).thenReturn(Optional.of(route));
            when(mcpToolRegistry.getClient("github")).thenReturn(Optional.of(mcpClient));
            when(mcpClient.callTool("get_issue", Map.of()))
                    .thenThrow(new RuntimeException("연결 타임아웃"));

            LlmToolResult result = executor.execute(toolCall);

            assertThat(result.output()).contains("[오류]");
            assertThat(result.output()).contains("연결 타임아웃");
        }

        @Test
        @DisplayName("toolCall이 null이면 NullPointerException이 발생한다")
        void throwsOnNullToolCall() {
            assertThatThrownBy(() -> executor.execute(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
