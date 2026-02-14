package com.squad.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squad.mcp.jsonrpc.JsonRpcNotification;
import com.squad.mcp.jsonrpc.JsonRpcRequest;
import com.squad.mcp.jsonrpc.JsonRpcResponse;
import com.squad.mcp.process.McpConnection;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 서버와 JSON-RPC 2.0 프로토콜로 통신하는 클라이언트.
 *
 * <p>stdio 기반 전송 계층을 사용하여 MCP 서버 프로세스의 stdin/stdout으로
 * JSON-RPC 메시지를 주고받는다. 줄바꿈({@code \n})으로 메시지를 구분한다.</p>
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>{@link #initialize()} - MCP 프로토콜 핸드셰이크 (3단계)</li>
 *   <li>{@link #listTools()} - 서버가 제공하는 도구 목록 조회</li>
 *   <li>{@link #callTool(String, Map)} - 특정 도구 호출</li>
 * </ul>
 *
 * <p>Phase 1에서는 동기식 단일 in-flight 요청 방식으로 동작한다.
 * 모든 public 메서드는 {@code synchronized}로 보호되어 동시 호출 시에도
 * 단일 요청만 처리된다. 응답 대기 중 수신되는 notification은 로깅 후 스킵한다.</p>
 *
 * @see McpConnection
 */
@Slf4j
public class McpClient {

    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private final McpConnection connection;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIdCounter;
    private final long timeoutMillis;

    private volatile boolean initialized;

    /**
     * 기본 타임아웃(30초)으로 MCP 클라이언트를 생성한다.
     *
     * @param connection   MCP 서버 연결
     * @param objectMapper JSON 직렬화/역직렬화에 사용할 ObjectMapper
     */
    public McpClient(McpConnection connection, ObjectMapper objectMapper) {
        this(connection, objectMapper, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * 커스텀 타임아웃으로 MCP 클라이언트를 생성한다.
     *
     * @param connection    MCP 서버 연결
     * @param objectMapper  JSON 직렬화/역직렬화에 사용할 ObjectMapper
     * @param timeoutMillis 응답 대기 타임아웃 (밀리초, 양수)
     * @throws IllegalArgumentException timeoutMillis가 0 이하인 경우
     */
    public McpClient(McpConnection connection, ObjectMapper objectMapper, long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis는 양수여야 합니다: " + timeoutMillis);
        }
        this.connection = connection;
        this.objectMapper = objectMapper;
        this.timeoutMillis = timeoutMillis;
        this.requestIdCounter = new AtomicLong(1);
        this.initialized = false;
    }

    /**
     * MCP 프로토콜 초기화 핸드셰이크를 수행한다.
     *
     * <p>3단계 핸드셰이크:</p>
     * <ol>
     *   <li>initialize 요청 전송</li>
     *   <li>initialize 응답 수신 (서버 capabilities 확인)</li>
     *   <li>initialized 알림 전송</li>
     * </ol>
     *
     * @return 서버의 initialize 응답 결과
     * @throws McpClientException 초기화 실패 시
     */
    public synchronized JsonNode initialize() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode capabilities = objectMapper.createObjectNode();
        params.set("capabilities", capabilities);

        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "squad-mcp-client");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);

        JsonRpcResponse response = sendRequest("initialize", params);

        if (response.isError()) {
            throw new McpClientException(
                    "MCP initialize 실패: " + response.error().message(),
                    response.error());
        }

        sendNotification("notifications/initialized");

        initialized = true;
        log.info("MCP 초기화 완료: name={}", connection.getName());

        return response.result();
    }

    /**
     * MCP 서버가 제공하는 도구 목록을 조회한다.
     *
     * @return 도구 목록 (name, description, inputSchema 포함)
     * @throws McpClientException      조회 실패 시
     * @throws IllegalStateException   초기화되지 않은 상태에서 호출 시
     */
    public synchronized List<McpToolInfo> listTools() {
        ensureInitialized();

        JsonRpcResponse response = sendRequest("tools/list", null);

        if (response.isError()) {
            throw new McpClientException(
                    "MCP tools/list 실패: " + response.error().message(),
                    response.error());
        }

        return parseToolList(response.result());
    }

    /**
     * MCP 서버의 특정 도구를 호출한다.
     *
     * @param toolName  호출할 도구 이름
     * @param arguments 도구에 전달할 인자
     * @return 도구 호출 결과
     * @throws McpClientException      호출 실패 시
     * @throws IllegalStateException   초기화되지 않은 상태에서 호출 시
     */
    public synchronized McpToolCallResult callTool(String toolName, Map<String, Object> arguments) {
        ensureInitialized();

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", objectMapper.valueToTree(arguments));

        JsonRpcResponse response = sendRequest("tools/call", params);

        if (response.isError()) {
            throw new McpClientException(
                    "MCP tools/call 실패 [" + toolName + "]: " + response.error().message(),
                    response.error());
        }

        return parseToolCallResult(response.result());
    }

    /**
     * 초기화 여부를 반환한다.
     *
     * @return 초기화 완료 시 {@code true}
     */
    public boolean isInitialized() {
        return initialized;
    }

    private JsonRpcResponse sendRequest(String method, JsonNode params) {
        String id = String.valueOf(requestIdCounter.getAndIncrement());
        JsonRpcRequest request = JsonRpcRequest.of(id, method, params);

        sendMessage(request);

        return readResponse(id);
    }

    private void sendNotification(String method) {
        JsonRpcNotification notification = JsonRpcNotification.of(method);
        sendMessage(notification);
    }

    private void sendMessage(Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            BufferedWriter writer = connection.getStdin();

            writer.write(json);
            writer.newLine();
            writer.flush();

            log.debug("MCP 메시지 전송: name={}, message={}", connection.getName(), json);
        } catch (IOException e) {
            throw new McpClientException(
                    "MCP 메시지 전송 실패: name=" + connection.getName(), e);
        }
    }

    private JsonRpcResponse readResponse(String expectedId) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        BufferedReader reader = connection.getStdout();

        while (System.currentTimeMillis() < deadline) {
            try {
                if (!connection.isAlive() && !reader.ready()) {
                    throw new McpClientException(
                            "MCP 프로세스가 종료됨: name=" + connection.getName());
                }

                if (!reader.ready()) {
                    sleep(10);
                    continue;
                }

                String line = reader.readLine();
                if (line == null) {
                    throw new McpClientException(
                            "MCP 스트림이 종료됨: name=" + connection.getName());
                }

                if (line.isBlank()) {
                    continue;
                }

                log.debug("MCP 메시지 수신: name={}, message={}", connection.getName(), line);

                JsonNode node = parseJsonLine(line);

                if (isServerRequest(node)) {
                    handleServerRequest(node);
                    continue;
                }

                if (isNotification(node)) {
                    log.debug("MCP notification 수신 (스킵): name={}, method={}",
                            connection.getName(), node.path("method").asText());
                    continue;
                }

                JsonRpcResponse response = objectMapper.treeToValue(node, JsonRpcResponse.class);

                if (!expectedId.equals(response.id())) {
                    log.warn("MCP 응답 id 불일치: expected={}, actual={}, name={}",
                            expectedId, response.id(), connection.getName());
                    continue;
                }

                return response;

            } catch (McpClientException e) {
                throw e;
            } catch (IOException e) {
                throw new McpClientException(
                        "MCP 응답 읽기 실패: name=" + connection.getName(), e);
            }
        }

        throw new McpClientException(
                "MCP 응답 타임아웃 (" + timeoutMillis + "ms): name=" + connection.getName());
    }

    private JsonNode parseJsonLine(String line) {
        try {
            JsonNode node = objectMapper.readTree(line);
            if (node == null || !node.isObject()) {
                throw new McpClientException(
                        "MCP 메시지가 JSON 객체가 아닙니다: name=" + connection.getName());
            }
            return node;
        } catch (IOException e) {
            throw new McpClientException(
                    "MCP 메시지 JSON 파싱 실패: name=" + connection.getName(), e);
        }
    }

    private boolean isNotification(JsonNode node) {
        return node.has("method") && !node.has("id");
    }

    private boolean isServerRequest(JsonNode node) {
        return node.has("method") && node.has("id");
    }

    private void handleServerRequest(JsonNode node) {
        JsonNode idNode = node.path("id");
        String method = node.path("method").asText();

        log.warn("MCP 서버 요청 수신 (미지원): name={}, method={}, id={}",
                connection.getName(), method, idNode);

        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("jsonrpc", "2.0");
        errorResponse.set("id", idNode);

        ObjectNode errorObj = objectMapper.createObjectNode();
        errorObj.put("code", -32601);
        errorObj.put("message", "Method not found");
        errorResponse.set("error", errorObj);

        sendMessage(errorResponse);
    }

    private List<McpToolInfo> parseToolList(JsonNode result) {
        if (result == null || result.isNull() || !result.isObject()) {
            throw new McpClientException(
                    "MCP tools/list 응답의 result가 유효하지 않습니다: name=" + connection.getName());
        }

        List<McpToolInfo> tools = new ArrayList<>();
        JsonNode toolsNode = result.path("tools");

        if (toolsNode.isMissingNode() || !toolsNode.isArray()) {
            return tools;
        }

        for (JsonNode toolNode : toolsNode) {
            String name = toolNode.path("name").asText();
            if (name.isEmpty()) {
                log.warn("MCP 도구에 name이 없어 스킵: name={}", connection.getName());
                continue;
            }
            String description = toolNode.path("description").asText("");
            JsonNode inputSchema = toolNode.path("inputSchema");

            tools.add(new McpToolInfo(name, description, inputSchema));
        }

        return tools;
    }

    private McpToolCallResult parseToolCallResult(JsonNode result) {
        if (result == null || result.isNull() || !result.isObject()) {
            throw new McpClientException(
                    "MCP tools/call 응답의 result가 유효하지 않습니다: name=" + connection.getName());
        }

        List<McpToolCallResult.Content> contentList = new ArrayList<>();
        JsonNode contentNode = result.path("content");

        if (contentNode.isArray()) {
            for (JsonNode item : contentNode) {
                String type = item.path("type").asText("text");
                String text = item.path("text").asText("");
                contentList.add(new McpToolCallResult.Content(type, text));
            }
        }

        boolean isError = result.path("isError").asBoolean(false);
        return new McpToolCallResult(contentList, isError);
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "MCP 클라이언트가 초기화되지 않았습니다. initialize()를 먼저 호출하세요.");
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpClientException("MCP 응답 대기 중 인터럽트", e);
        }
    }
}
