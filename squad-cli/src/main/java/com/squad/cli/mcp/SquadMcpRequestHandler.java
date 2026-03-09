package com.squad.cli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * MCP JSON-RPC 요청 라우터.
 */
@Component
public class SquadMcpRequestHandler {

    private static final String JSON_RPC_VERSION = "2.0";

    private final ObjectMapper objectMapper;
    private final SquadMcpToolService toolService;

    public SquadMcpRequestHandler(ObjectMapper objectMapper, SquadMcpToolService toolService) {
        this.objectMapper = objectMapper;
        this.toolService = toolService;
    }

    public JsonNode handleLine(String line) {
        JsonNode request;
        try {
            request = objectMapper.readTree(line);
        } catch (Exception e) {
            return errorResponse(null, -32700, "Parse error");
        }

        return handleRequest(request);
    }

    JsonNode handleRequest(JsonNode request) {
        if (request == null || !request.isObject()) {
            return errorResponse(null, -32600, "Invalid Request");
        }

        JsonNode id = request.get("id");
        String method = textOrNull(request.get("method"));

        if (!JSON_RPC_VERSION.equals(textOrNull(request.get("jsonrpc"))) || method == null) {
            return errorResponse(id, -32600, "Invalid Request");
        }

        if ("notifications/initialized".equals(method) && id == null) {
            return null;
        }

        if (id == null) {
            return null;
        }

        return switch (method) {
            case "initialize" -> successResponse(id, initializeResult());
            case "tools/list" -> successResponse(id, toolsListResult());
            case "tools/call" -> handleToolsCall(id, request.get("params"));
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    private JsonNode handleToolsCall(JsonNode id, JsonNode params) {
        if (params == null || !params.isObject()) {
            return errorResponse(id, -32602, "Invalid params: object required");
        }

        String toolName = textOrNull(params.get("name"));
        if (toolName == null || toolName.isBlank()) {
            return errorResponse(id, -32602, "Invalid params: name is required");
        }

        JsonNode arguments = params.get("arguments");
        if (arguments == null || arguments.isNull()) {
            arguments = objectMapper.createObjectNode();
        }
        if (!arguments.isObject()) {
            return errorResponse(id, -32602, "Invalid params: arguments must be object");
        }

        return successResponse(id, toolService.callTool(toolName, arguments));
    }

    private ObjectNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2025-03-26");

        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.set("tools", objectMapper.createObjectNode());
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "squad-mcp-server");
        serverInfo.put("version", "0.1.0");
        result.set("serverInfo", serverInfo);

        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", toolService.listTools());
        return result;
    }

    private ObjectNode successResponse(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSON_RPC_VERSION);
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode errorResponse(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", JSON_RPC_VERSION);
        if (id == null) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }

        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return response;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.asText();
    }
}
