package com.squad.cli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squad.cli.api.SquadApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SquadMcpToolServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SquadApiClient apiClient = mock(SquadApiClient.class);
    private final SquadMcpToolService service = new SquadMcpToolService(apiClient, objectMapper);

    @Test
    @DisplayName("squad_list_agents 호출 시 성공 결과를 반환한다")
    void listAgents() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("id", 1).put("name", "agent-1")));
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.of(response));

        ObjectNode result = service.callTool("squad_list_agents", objectMapper.createObjectNode());

        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("content").get(0).path("text").asText()).contains("agent-1");
    }

    @Test
    @DisplayName("squad_run_session은 생성 후 시작을 호출한다")
    void runSession() {
        ObjectNode createResponse = objectMapper.createObjectNode();
        createResponse.put("success", true);
        createResponse.set("data", objectMapper.createObjectNode().put("id", 99).put("status", "PENDING"));

        ObjectNode startResponse = objectMapper.createObjectNode();
        startResponse.put("success", true);
        startResponse.set("data", objectMapper.createObjectNode().put("id", 99).put("status", "RUNNING"));

        when(apiClient.post(eq("/api/v1/sessions"), any())).thenReturn(Optional.of(createResponse));
        when(apiClient.post(eq("/api/v1/sessions/99/start"), eq(Map.of()))).thenReturn(Optional.of(startResponse));

        JsonNode args = objectMapper.createObjectNode()
                .put("squadId", 10)
                .put("userPrompt", "테스트 실행");

        ObjectNode result = service.callTool("squad_run_session", args);

        assertThat(result.path("isError").asBoolean()).isFalse();
        String text = result.path("content").get(0).path("text").asText();
        assertThat(text).contains("RUNNING");
    }

    @Test
    @DisplayName("알 수 없는 도구는 isError=true를 반환한다")
    void unknownTool() {
        ObjectNode result = service.callTool("unknown_tool", objectMapper.createObjectNode());

        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asText()).contains("알 수 없는 도구");
    }
}
