package com.squad.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.mcp.process.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpClientTest {

    @Mock
    private McpConnection connection;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PipedOutputStream serverOutput;
    private BufferedWriter clientStdin;
    private ByteArrayOutputStream clientStdinCapture;

    @BeforeEach
    void setUp() throws Exception {
        serverOutput = new PipedOutputStream();
        PipedInputStream clientInput = new PipedInputStream(serverOutput);
        BufferedReader stdout = new BufferedReader(
                new InputStreamReader(clientInput, StandardCharsets.UTF_8));

        clientStdinCapture = new ByteArrayOutputStream();
        clientStdin = new BufferedWriter(
                new OutputStreamWriter(clientStdinCapture, StandardCharsets.UTF_8));

        lenient().when(connection.getStdout()).thenReturn(stdout);
        lenient().when(connection.getStdin()).thenReturn(clientStdin);
        lenient().when(connection.getName()).thenReturn("test-mcp");
        lenient().when(connection.isAlive()).thenReturn(true);
    }

    private void sendServerResponse(String json) throws IOException {
        serverOutput.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        serverOutput.flush();
    }

    @Nested
    @DisplayName("initialize")
    class InitializeTest {

        @Test
        @DisplayName("3단계 핸드셰이크를 성공적으로 수행한다")
        void successfulHandshake() throws Exception {
            McpClient client = new McpClient(connection, objectMapper, 5000L);

            String initResponse = """
                    {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2025-03-26","capabilities":{"tools":{}},"serverInfo":{"name":"test-server","version":"1.0.0"}}}""";

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse(initResponse);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            var result = client.initialize();
            responseThread.join();

            assertThat(client.isInitialized()).isTrue();
            assertThat(result.path("protocolVersion").asText()).isEqualTo("2025-03-26");
            assertThat(result.path("serverInfo").path("name").asText()).isEqualTo("test-server");
        }

        @Test
        @DisplayName("에러 응답 시 McpClientException이 발생한다")
        void failsOnErrorResponse() throws Exception {
            McpClient client = new McpClient(connection, objectMapper, 5000L);

            String errorResponse = """
                    {"jsonrpc":"2.0","id":"1","error":{"code":-32600,"message":"Unsupported protocol version"}}""";

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse(errorResponse);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            assertThatThrownBy(client::initialize)
                    .isInstanceOf(McpClientException.class)
                    .hasMessageContaining("initialize 실패");

            responseThread.join();
            assertThat(client.isInitialized()).isFalse();
        }
    }

    @Nested
    @DisplayName("listTools")
    class ListToolsTest {

        @Test
        @DisplayName("도구 목록을 성공적으로 조회한다")
        void successfulListTools() throws Exception {
            McpClient client = createInitializedClient();

            String toolsResponse = """
                    {"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"get_issue","description":"Get a GitHub issue","inputSchema":{"type":"object","properties":{"owner":{"type":"string"}}}}]}}""";

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse(toolsResponse);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            List<McpToolInfo> tools = client.listTools();
            responseThread.join();

            assertThat(tools).hasSize(1);
            assertThat(tools.get(0).name()).isEqualTo("get_issue");
            assertThat(tools.get(0).description()).isEqualTo("Get a GitHub issue");
            assertThat(tools.get(0).inputSchema()).isNotNull();
        }

        @Test
        @DisplayName("초기화 전 호출 시 IllegalStateException이 발생한다")
        void failsBeforeInitialization() {
            McpClient client = new McpClient(connection, objectMapper);

            assertThatThrownBy(client::listTools)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("초기화되지 않았습니다");
        }
    }

    @Nested
    @DisplayName("callTool")
    class CallToolTest {

        @Test
        @DisplayName("도구를 성공적으로 호출한다")
        void successfulCallTool() throws Exception {
            McpClient client = createInitializedClient();

            String callResponse = """
                    {"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Issue #1: Bug fix"}],"isError":false}}""";

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse(callResponse);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            McpToolCallResult result = client.callTool("get_issue", Map.of("owner", "test"));
            responseThread.join();

            assertThat(result.isError()).isFalse();
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).type()).isEqualTo("text");
            assertThat(result.content().get(0).text()).isEqualTo("Issue #1: Bug fix");
        }

        @Test
        @DisplayName("도구 호출 에러 응답을 처리한다")
        void handlesToolCallError() throws Exception {
            McpClient client = createInitializedClient();

            String errorResponse = """
                    {"jsonrpc":"2.0","id":"2","error":{"code":-32602,"message":"Unknown tool"}}""";

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse(errorResponse);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            assertThatThrownBy(() -> client.callTool("unknown", Map.of()))
                    .isInstanceOf(McpClientException.class)
                    .hasMessageContaining("tools/call 실패");

            responseThread.join();
        }

        @Test
        @DisplayName("도구 실행 결과에 isError가 true인 경우를 처리한다")
        void handlesToolExecutionError() throws Exception {
            McpClient client = createInitializedClient();

            String callResponse = """
                    {"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"Error: not found"}],"isError":true}}""";

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse(callResponse);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            McpToolCallResult result = client.callTool("get_issue", Map.of("owner", "test"));
            responseThread.join();

            assertThat(result.isError()).isTrue();
            assertThat(result.content().get(0).text()).contains("Error");
        }
    }

    @Nested
    @DisplayName("notification 처리")
    class NotificationHandlingTest {

        @Test
        @DisplayName("응답 사이에 끼어든 notification을 스킵한다")
        void skipsInterleavedNotifications() throws Exception {
            McpClient client = createInitializedClient();

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse("""
                            {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":50}}""");
                    Thread.sleep(10);
                    sendServerResponse("""
                            {"jsonrpc":"2.0","id":"2","result":{"tools":[]}}""");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            List<McpToolInfo> tools = client.listTools();
            responseThread.join();

            assertThat(tools).isEmpty();
        }
    }

    @Nested
    @DisplayName("서버 요청 처리")
    class ServerRequestHandlingTest {

        @Test
        @DisplayName("서버 요청이 끼어들어도 정상 응답을 받을 수 있다")
        void handlesServerRequestAndContinues() throws Exception {
            McpClient client = createInitializedClient();

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse("""
                            {"jsonrpc":"2.0","id":"server-1","method":"sampling/createMessage","params":{}}""");
                    Thread.sleep(10);
                    sendServerResponse("""
                            {"jsonrpc":"2.0","id":"2","result":{"tools":[]}}""");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            List<McpToolInfo> tools = client.listTools();
            responseThread.join();

            assertThat(tools).isEmpty();
        }

        @Test
        @DisplayName("서버 요청의 숫자 id가 에러 응답에서 보존된다")
        void preservesNumericIdInErrorResponse() throws Exception {
            McpClient client = createInitializedClient();

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse("""
                            {"jsonrpc":"2.0","id":42,"method":"sampling/createMessage","params":{}}""");
                    Thread.sleep(10);
                    sendServerResponse("""
                            {"jsonrpc":"2.0","id":"2","result":{"tools":[]}}""");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            List<McpToolInfo> tools = client.listTools();
            responseThread.join();

            assertThat(tools).isEmpty();

            clientStdin.flush();
            String captured = clientStdinCapture.toString(StandardCharsets.UTF_8);
            assertThat(captured).contains("\"id\":42");
        }
    }

    @Nested
    @DisplayName("타임아웃")
    class TimeoutTest {

        @Test
        @DisplayName("응답 타임아웃 시 McpClientException이 발생한다")
        void throwsOnTimeout() {
            McpClient client = new McpClient(connection, objectMapper, 200L);

            initializeClientForTimeout(client);

            assertThatThrownBy(client::listTools)
                    .isInstanceOf(McpClientException.class)
                    .hasMessageContaining("타임아웃");
        }

        private void initializeClientForTimeout(McpClient client) {
            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse("""
                            {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2025-03-26","capabilities":{},"serverInfo":{"name":"test","version":"1.0"}}}""");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();
            client.initialize();
            try {
                responseThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nested
    @DisplayName("생성자 검증")
    class ConstructorValidationTest {

        @Test
        @DisplayName("timeoutMillis가 0이면 IllegalArgumentException이 발생한다")
        void throwsOnZeroTimeout() {
            assertThatThrownBy(() -> new McpClient(connection, objectMapper, 0L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("양수");
        }

        @Test
        @DisplayName("timeoutMillis가 음수이면 IllegalArgumentException이 발생한다")
        void throwsOnNegativeTimeout() {
            assertThatThrownBy(() -> new McpClient(connection, objectMapper, -1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("양수");
        }
    }

    @Nested
    @DisplayName("malformed JSON 처리")
    class MalformedJsonTest {

        @Test
        @DisplayName("잘못된 JSON 수신 시 McpClientException이 발생한다")
        void throwsOnMalformedJson() throws Exception {
            McpClient client = createInitializedClient();

            Thread responseThread = new Thread(() -> {
                try {
                    Thread.sleep(50);
                    sendServerResponse("this is not json");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            responseThread.start();

            assertThatThrownBy(client::listTools)
                    .isInstanceOf(McpClientException.class)
                    .hasMessageContaining("JSON 파싱 실패");

            responseThread.join();
        }
    }

    @Nested
    @DisplayName("프로세스 종료")
    class ProcessDeathTest {

        @Test
        @DisplayName("프로세스 종료 시 McpClientException이 발생한다")
        void throwsOnProcessDeath() throws Exception {
            when(connection.isAlive()).thenReturn(true, false);

            McpClient client = createInitializedClient();

            serverOutput.close();

            assertThatThrownBy(client::listTools)
                    .isInstanceOf(McpClientException.class);
        }
    }

    private McpClient createInitializedClient() {
        McpClient client = new McpClient(connection, objectMapper, 5000L);

        String initResponse = """
                {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2025-03-26","capabilities":{},"serverInfo":{"name":"test","version":"1.0"}}}""";

        Thread responseThread = new Thread(() -> {
            try {
                Thread.sleep(50);
                sendServerResponse(initResponse);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        responseThread.start();

        client.initialize();

        try {
            responseThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return client;
    }
}
