package com.squad.mcp.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.llm.model.LlmTool;
import com.squad.mcp.client.McpClient;
import com.squad.mcp.client.McpToolInfo;
import com.squad.mcp.process.McpConfig;
import com.squad.mcp.process.McpConnection;
import com.squad.mcp.process.McpProcessManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpToolRegistry")
class McpToolRegistryTest {

    @Mock
    private McpProcessManager processManager;

    @Mock
    private McpConnection connection;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private McpToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new McpToolRegistry(processManager, objectMapper);
    }

    private McpConfig createMcpConfig(String name) {
        return mock(McpConfig.class, invocation -> {
            if (invocation.getMethod().getName().equals("getName")) {
                return name;
            }
            return invocation.callRealMethod();
        });
    }

    private JsonNode createSchema(String property) throws Exception {
        String json = """
                {"type":"object","properties":{"%s":{"type":"string"}},"required":["%s"]}
                """.formatted(property, property);
        return objectMapper.readTree(json);
    }

    @Nested
    @DisplayName("registerMcp")
    class RegisterMcpTest {

        @Test
        @DisplayName("MCP를 등록하면 도구 목록이 LlmTool로 변환되어 반환된다")
        void registersAndReturnsLlmTools() throws Exception {
            McpConfig config = createMcpConfig("github");
            when(processManager.start(config)).thenReturn(connection);

            List<McpToolInfo> mockTools = List.of(
                    new McpToolInfo("get_issue", "Get a GitHub issue", createSchema("owner")),
                    new McpToolInfo("list_repos", "List repositories", createSchema("org"))
            );

            try (MockedConstruction<McpClient> mocked = mockConstruction(McpClient.class,
                    (mock, context) -> {
                        when(mock.listTools()).thenReturn(mockTools);
                    })) {

                List<LlmTool> tools = registry.registerMcp(config);

                assertThat(tools).hasSize(2);
                assertThat(tools.get(0).name()).isEqualTo("github__get_issue");
                assertThat(tools.get(0).description()).isEqualTo("Get a GitHub issue");
                assertThat(tools.get(0).inputSchema()).containsKey("properties");
                assertThat(tools.get(1).name()).isEqualTo("github__list_repos");

                McpClient client = mocked.constructed().get(0);
                verify(client).initialize();
                verify(client).listTools();
            }
        }

        @Test
        @DisplayName("이미 등록된 MCP를 재등록하면 기존 캐시가 무효화된다")
        void reregistrationInvalidatesOldCache() throws Exception {
            McpConfig config = createMcpConfig("github");
            when(processManager.start(config)).thenReturn(connection);

            List<McpToolInfo> firstTools = List.of(
                    new McpToolInfo("get_issue", "Get issue", createSchema("id"))
            );
            List<McpToolInfo> secondTools = List.of(
                    new McpToolInfo("create_pr", "Create PR", createSchema("title"))
            );

            // 첫 번째 등록
            try (MockedConstruction<McpClient> ignored = mockConstruction(McpClient.class,
                    (mock, context) -> when(mock.listTools()).thenReturn(firstTools))) {
                registry.registerMcp(config);
            }

            // 재등록
            try (MockedConstruction<McpClient> ignored = mockConstruction(McpClient.class,
                    (mock, context) -> when(mock.listTools()).thenReturn(secondTools))) {
                List<LlmTool> tools = registry.registerMcp(config);

                assertThat(tools).hasSize(1);
                assertThat(tools.get(0).name()).isEqualTo("github__create_pr");

                // 이전 도구 라우트가 제거되었는지 확인
                assertThat(registry.findRoute("github__get_issue")).isEmpty();
                assertThat(registry.findRoute("github__create_pr")).isPresent();
            }
        }

        @Test
        @DisplayName("초기화 실패 시 프로세스를 정리하고 예외를 전파한다")
        void rollsBackOnInitializeFailure() {
            McpConfig config = createMcpConfig("github");
            when(processManager.start(config)).thenReturn(connection);

            try (MockedConstruction<McpClient> ignored = mockConstruction(McpClient.class,
                    (mock, context) -> {
                        doThrow(new RuntimeException("초기화 실패")).when(mock).initialize();
                    })) {

                assertThatThrownBy(() -> registry.registerMcp(config))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessageContaining("초기화 실패");

                verify(processManager).stop("github");
                assertThat(registry.getClient("github")).isEmpty();
                assertThat(registry.getTools("github")).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("getTools")
    class GetToolsTest {

        @Test
        @DisplayName("등록된 MCP의 도구 목록을 반환한다")
        void returnsToolsForRegisteredMcp() throws Exception {
            registerGithubMcp();

            List<LlmTool> tools = registry.getTools("github");

            assertThat(tools).hasSize(1);
            assertThat(tools.get(0).name()).isEqualTo("github__get_issue");
        }

        @Test
        @DisplayName("등록되지 않은 MCP에 대해 빈 리스트를 반환한다")
        void returnsEmptyForUnregisteredMcp() {
            List<LlmTool> tools = registry.getTools("unknown");

            assertThat(tools).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAllTools")
    class GetAllToolsTest {

        @Test
        @DisplayName("모든 MCP의 도구 목록을 통합하여 반환한다")
        void returnsAllToolsFromAllMcps() throws Exception {
            registerGithubMcp();
            registerSlackMcp();

            List<LlmTool> allTools = registry.getAllTools();

            assertThat(allTools).hasSize(2);
            assertThat(allTools).extracting(LlmTool::name)
                    .containsExactlyInAnyOrder("github__get_issue", "slack__send_message");
        }
    }

    @Nested
    @DisplayName("getToolsForMcps")
    class GetToolsForMcpsTest {

        @Test
        @DisplayName("지정된 MCP들의 도구만 반환한다")
        void returnsToolsForSpecifiedMcps() throws Exception {
            registerGithubMcp();
            registerSlackMcp();

            List<LlmTool> tools = registry.getToolsForMcps(List.of("github"));

            assertThat(tools).hasSize(1);
            assertThat(tools.get(0).name()).isEqualTo("github__get_issue");
        }

        @Test
        @DisplayName("등록되지 않은 MCP는 건너뛴다")
        void skipsUnregisteredMcps() throws Exception {
            registerGithubMcp();

            List<LlmTool> tools = registry.getToolsForMcps(List.of("github", "unknown"));

            assertThat(tools).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findRoute")
    class FindRouteTest {

        @Test
        @DisplayName("등록된 도구의 라우팅 정보를 반환한다")
        void findsRouteForRegisteredTool() throws Exception {
            registerGithubMcp();

            Optional<ToolRoute> route = registry.findRoute("github__get_issue");

            assertThat(route).isPresent();
            assertThat(route.get().mcpName()).isEqualTo("github");
            assertThat(route.get().originalToolName()).isEqualTo("get_issue");
        }

        @Test
        @DisplayName("등록되지 않은 alias에 대해 empty를 반환한다")
        void returnsEmptyForUnknownAlias() {
            Optional<ToolRoute> route = registry.findRoute("unknown__tool");

            assertThat(route).isEmpty();
        }
    }

    @Nested
    @DisplayName("unregisterMcp")
    class UnregisterMcpTest {

        @Test
        @DisplayName("MCP 등록 해제 시 도구, 라우트가 제거되고 프로세스가 종료된다")
        void removesToolsAndRoutesAndStopsProcess() throws Exception {
            registerGithubMcp();

            registry.unregisterMcp("github");

            assertThat(registry.getTools("github")).isEmpty();
            assertThat(registry.findRoute("github__get_issue")).isEmpty();
            assertThat(registry.getClient("github")).isEmpty();
            assertThat(registry.getRegisteredMcpNames()).doesNotContain("github");
            verify(processManager).stop("github");
        }
    }

    @Nested
    @DisplayName("unregisterAll")
    class UnregisterAllTest {

        @Test
        @DisplayName("모든 등록이 해제되고 프로세스가 종료된다")
        void clearsAllAndStopsProcesses() throws Exception {
            registerGithubMcp();
            registerSlackMcp();

            registry.unregisterAll();

            assertThat(registry.getAllTools()).isEmpty();
            assertThat(registry.getRegisteredMcpNames()).isEmpty();
            verify(processManager).stop("github");
            verify(processManager).stop("slack");
        }
    }

    @Nested
    @DisplayName("getClient")
    class GetClientTest {

        @Test
        @DisplayName("등록된 MCP의 클라이언트를 반환한다")
        void returnsClientForRegisteredMcp() throws Exception {
            registerGithubMcp();

            Optional<McpClient> client = registry.getClient("github");

            assertThat(client).isPresent();
        }

        @Test
        @DisplayName("등록되지 않은 MCP에 대해 empty를 반환한다")
        void returnsEmptyForUnregisteredMcp() {
            Optional<McpClient> client = registry.getClient("unknown");

            assertThat(client).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRegisteredMcpNames")
    class GetRegisteredMcpNamesTest {

        @Test
        @DisplayName("등록된 모든 MCP 이름을 반환한다")
        void returnsAllRegisteredNames() throws Exception {
            registerGithubMcp();
            registerSlackMcp();

            Set<String> names = registry.getRegisteredMcpNames();

            assertThat(names).containsExactlyInAnyOrder("github", "slack");
        }
    }

    private void registerGithubMcp() throws Exception {
        McpConfig config = createMcpConfig("github");
        when(processManager.start(config)).thenReturn(connection);

        List<McpToolInfo> tools = List.of(
                new McpToolInfo("get_issue", "Get a GitHub issue", createSchema("owner"))
        );

        try (MockedConstruction<McpClient> ignored = mockConstruction(McpClient.class,
                (mock, context) -> when(mock.listTools()).thenReturn(tools))) {
            registry.registerMcp(config);
        }
    }

    private void registerSlackMcp() throws Exception {
        McpConfig config = createMcpConfig("slack");
        when(processManager.start(config)).thenReturn(connection);

        List<McpToolInfo> tools = List.of(
                new McpToolInfo("send_message", "Send a Slack message", createSchema("channel"))
        );

        try (MockedConstruction<McpClient> ignored = mockConstruction(McpClient.class,
                (mock, context) -> when(mock.listTools()).thenReturn(tools))) {
            registry.registerMcp(config);
        }
    }
}
