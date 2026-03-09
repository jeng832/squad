package com.squad.cli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squad.cli.api.SquadApiClient;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 도구 정의/실행 서비스.
 */
@Component
public class SquadMcpToolService {

    private final SquadApiClient apiClient;
    private final ObjectMapper objectMapper;

    public SquadMcpToolService(SquadApiClient apiClient, ObjectMapper objectMapper) {
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
    }

    public ArrayNode listTools() {
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(tool("squad_health", "Squad 서버 상태를 확인합니다.", emptySchema()));
        tools.add(tool("squad_list_agents", "에이전트 목록을 조회합니다.", emptySchema()));
        tools.add(tool("squad_list_squads", "Squad 목록을 조회합니다.", emptySchema()));
        tools.add(tool("squad_list_sessions", "세션 목록을 조회합니다.", emptySchema()));
        tools.add(tool("squad_get_session", "세션 상세 정보를 조회합니다.",
                schema(Map.of("sessionId", numberProperty("조회할 세션 ID")), "sessionId")));
        tools.add(tool("squad_get_session_messages", "세션 메시지를 조회합니다.",
                schema(Map.of(
                        "sessionId", numberProperty("조회할 세션 ID"),
                        "type", stringProperty("메시지 타입(TASK_REQUEST, TASK_RESULT, HELP_REQUEST, HELP_RESPONSE, SYSTEM)")
                ), "sessionId")));
        tools.add(tool("squad_run_session", "세션을 생성하고 즉시 시작합니다.",
                schema(Map.of(
                        "squadId", numberProperty("실행할 Squad ID"),
                        "userPrompt", stringProperty("사용자 프롬프트"),
                        "repoUrl", stringProperty("Git 저장소 URL (선택)"),
                        "branch", stringProperty("브랜치명 (선택)"),
                        "gitProvider", stringProperty("git 제공자 (선택, 예: github)"),
                        "gitSecretName", stringProperty("git 토큰 Secret 이름 (선택)")
                ), "squadId", "userPrompt")));
        return tools;
    }

    public ObjectNode callTool(String toolName, JsonNode arguments) {
        try {
            JsonNode result = switch (toolName) {
                case "squad_health" -> health();
                case "squad_list_agents" -> fetchData("/api/v1/agents");
                case "squad_list_squads" -> fetchData("/api/v1/squads");
                case "squad_list_sessions" -> fetchData("/api/v1/sessions");
                case "squad_get_session" -> getSession(arguments);
                case "squad_get_session_messages" -> getSessionMessages(arguments);
                case "squad_run_session" -> runSession(arguments);
                default -> throw new IllegalArgumentException("알 수 없는 도구: " + toolName);
            };

            return toolResult(false, toJson(result));
        } catch (Exception e) {
            return toolResult(true, e.getMessage());
        }
    }

    private JsonNode health() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("reachable", apiClient.isServerReachable());
        return result;
    }

    private JsonNode getSession(JsonNode arguments) {
        long sessionId = requiredLong(arguments, "sessionId");
        return fetchData("/api/v1/sessions/" + sessionId);
    }

    private JsonNode getSessionMessages(JsonNode arguments) {
        long sessionId = requiredLong(arguments, "sessionId");
        String type = optionalText(arguments, "type");

        String path = "/api/v1/sessions/" + sessionId + "/messages";
        if (type != null && !type.isBlank()) {
            path += "?type=" + URLEncoder.encode(type.trim().toUpperCase(), StandardCharsets.UTF_8);
        }
        return fetchData(path);
    }

    private JsonNode runSession(JsonNode arguments) {
        long squadId = requiredLong(arguments, "squadId");
        String userPrompt = requiredText(arguments, "userPrompt");

        ObjectNode body = objectMapper.createObjectNode();
        body.put("squadId", squadId);
        body.put("userPrompt", userPrompt);
        putOptional(body, "repoUrl", optionalText(arguments, "repoUrl"));
        putOptional(body, "branch", optionalText(arguments, "branch"));
        putOptional(body, "gitProvider", optionalText(arguments, "gitProvider"));
        putOptional(body, "gitSecretName", optionalText(arguments, "gitSecretName"));

        JsonNode created = requireData(apiClient.post("/api/v1/sessions", body), "세션 생성");
        long sessionId = requiredLong(created, "id");
        JsonNode started = requireData(apiClient.post("/api/v1/sessions/" + sessionId + "/start", Map.of()), "세션 시작");

        ObjectNode result = objectMapper.createObjectNode();
        result.set("created", created);
        result.set("started", started);
        return result;
    }

    private JsonNode fetchData(String path) {
        return requireData(apiClient.get(path), "GET " + path);
    }

    private JsonNode requireData(Optional<JsonNode> responseOpt, String operationName) {
        JsonNode response = responseOpt.orElseThrow(
                () -> new IllegalStateException("Squad API 호출 실패: " + operationName)
        );

        if (response.has("success") && !response.path("success").asBoolean()) {
            String message = response.path("message").asText("요청 실패");
            throw new IllegalStateException("Squad API 오류: " + message);
        }

        return response.path("data");
    }

    private ObjectNode toolResult(boolean isError, String text) {
        ObjectNode result = objectMapper.createObjectNode();

        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", text);
        content.add(textNode);

        result.set("content", content);
        result.put("isError", isError);
        return result;
    }

    private ObjectNode tool(String name, String description, ObjectNode inputSchema) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        tool.set("inputSchema", inputSchema);
        return tool;
    }

    private ObjectNode emptySchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode schema(Map<String, ObjectNode> properties, String... required) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode propertyNode = objectMapper.createObjectNode();
        properties.forEach(propertyNode::set);
        schema.set("properties", propertyNode);

        ArrayNode requiredNode = objectMapper.createArrayNode();
        for (String field : required) {
            requiredNode.add(field);
        }
        schema.set("required", requiredNode);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode numberProperty(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "number");
        node.put("description", description);
        return node;
    }

    private ObjectNode stringProperty(String description) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "string");
        node.put("description", description);
        return node;
    }

    private long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + "는 숫자여야 합니다.");
        }
        return value.asLong();
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 비어 있을 수 없습니다.");
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + "는 문자열이어야 합니다.");
        }
        return value.asText();
    }

    private void putOptional(ObjectNode node, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        node.put(key, value);
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("결과 직렬화 실패", e);
        }
    }
}
