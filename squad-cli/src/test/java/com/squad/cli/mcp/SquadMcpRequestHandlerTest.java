package com.squad.cli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SquadMcpRequestHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SquadMcpToolService toolService = mock(SquadMcpToolService.class);
    private final SquadMcpRequestHandler handler = new SquadMcpRequestHandler(objectMapper, toolService);

    @Test
    @DisplayName("initialize 요청을 처리한다")
    void initialize() {
        JsonNode response = handler.handleLine("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """);

        assertThat(response).isNotNull();
        assertThat(response.path("id").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("serverInfo").path("name").asText())
                .isEqualTo("squad-mcp-server");
    }

    @Test
    @DisplayName("tools/list 요청 시 toolService 목록을 반환한다")
    void toolsList() {
        ArrayNode tools = objectMapper.createArrayNode();
        tools.add(objectMapper.createObjectNode().put("name", "squad_health"));
        when(toolService.listTools()).thenReturn(tools);

        JsonNode response = handler.handleLine("""
                {"jsonrpc":"2.0","id":"abc","method":"tools/list"}
                """);

        assertThat(response).isNotNull();
        assertThat(response.path("result").path("tools")).hasSize(1);
        assertThat(response.path("result").path("tools").get(0).path("name").asText())
                .isEqualTo("squad_health");
    }

    @Test
    @DisplayName("tools/call 요청을 toolService로 위임한다")
    void toolsCall() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("isError", false);
        when(toolService.callTool(eq("squad_health"), any())).thenReturn(result);

        JsonNode response = handler.handleLine("""
                {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"squad_health","arguments":{}}}
                """);

        assertThat(response).isNotNull();
        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("unknown method는 method not found를 반환한다")
    void unknownMethod() {
        JsonNode response = handler.handleLine("""
                {"jsonrpc":"2.0","id":3,"method":"unknown/method"}
                """);

        assertThat(response).isNotNull();
        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32601);
    }

    @Test
    @DisplayName("initialized notification은 응답하지 않는다")
    void initializedNotification() {
        JsonNode response = handler.handleLine("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);

        assertThat(response).isNull();
    }
}
