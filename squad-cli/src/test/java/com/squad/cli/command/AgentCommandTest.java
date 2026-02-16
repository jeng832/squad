package com.squad.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squad.cli.api.SquadApiClient;
import com.squad.cli.form.InteractiveFormReader;
import com.squad.cli.shell.CommandContext;
import com.squad.cli.shell.CommandRegistry;
import com.squad.cli.ui.TableRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentCommandTest {

    private CommandRegistry registry;
    private SquadApiClient apiClient;
    private TableRenderer tableRenderer;
    private InteractiveFormReader formReader;
    private AgentCommand agentCommand;

    private StringWriter outputBuffer;
    private PrintWriter writer;
    private CommandContext ctx;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        apiClient = mock(SquadApiClient.class);
        tableRenderer = new TableRenderer();
        formReader = mock(InteractiveFormReader.class);

        agentCommand = new AgentCommand(registry, apiClient, tableRenderer, formReader);

        outputBuffer = new StringWriter();
        writer = new PrintWriter(outputBuffer);
        ctx = new CommandContext(null, null, writer);
    }

    @Test
    @DisplayName("agent 커맨드가 레지스트리에 등록된다")
    void registersAgentCommand() {
        assertThat(registry.find("agent")).isPresent();
        assertThat(registry.find("agent").get().description()).contains("에이전트");
    }

    @Test
    @DisplayName("/agent list - 에이전트 목록을 테이블로 출력한다")
    void listAgents() {
        ObjectNode response = createListResponse(
                createAgent(1L, "orchestrator", "ORCHESTRATOR", "claude-sonnet-4-20250514"),
                createAgent(2L, "worker-1", "WORKER", "claude-haiku-3")
        );
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.of(response));

        executeCommand("list");

        String output = outputBuffer.toString();
        assertThat(output).contains("orchestrator");
        assertThat(output).contains("worker-1");
        assertThat(output).contains("ORCHESTRATOR");
        assertThat(output).contains("WORKER");
    }

    @Test
    @DisplayName("/agent list - 에이전트가 없으면 안내 메시지를 출력한다")
    void listAgentsEmpty() {
        ObjectNode response = mapper.createObjectNode();
        response.putArray("data");
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.of(response));

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("등록된 에이전트가 없습니다");
    }

    @Test
    @DisplayName("/agent list - 서버 연결 실패 시 안내 메시지를 출력한다")
    void listAgentsServerError() {
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.empty());

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/agent {id} - 에이전트 상세 정보를 출력한다")
    void showAgentDetail() {
        ObjectNode response = createDetailResponse(1L, "orchestrator", "ORCHESTRATOR",
                "시스템 프롬프트 내용", "claude", "claude-sonnet-4-20250514", "ref:secret/key");
        when(apiClient.get("/api/v1/agents/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        String output = outputBuffer.toString();
        assertThat(output).contains("에이전트 상세");
        assertThat(output).contains("orchestrator");
        assertThat(output).contains("ORCHESTRATOR");
        assertThat(output).contains("시스템 프롬프트 내용");
    }

    @Test
    @DisplayName("/agent {id} - 존재하지 않는 에이전트 조회 시 안내 메시지를 출력한다")
    void showAgentDetailNotFound() {
        when(apiClient.get("/api/v1/agents/999")).thenReturn(Optional.empty());

        executeCommand("999");

        assertThat(outputBuffer.toString()).contains("에이전트를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/agent delete {id} - 확인 후 에이전트를 삭제한다")
    void deleteAgent() {
        ObjectNode response = createDetailResponse(1L, "test-agent", "WORKER",
                "role", "claude", "model", "key");
        when(apiClient.get("/api/v1/agents/1")).thenReturn(Optional.of(response));
        when(apiClient.delete("/api/v1/agents/1")).thenReturn(true);
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        executeCommand("delete 1");

        verify(apiClient).delete("/api/v1/agents/1");
        assertThat(outputBuffer.toString()).contains("에이전트가 삭제되었습니다");
    }

    @Test
    @DisplayName("/agent delete {id} - 취소하면 삭제하지 않는다")
    void deleteAgentCancelled() {
        ObjectNode response = createDetailResponse(1L, "test-agent", "WORKER",
                "role", "claude", "model", "key");
        when(apiClient.get("/api/v1/agents/1")).thenReturn(Optional.of(response));
        when(formReader.readConfirm(any(), any())).thenReturn(false);

        executeCommand("delete 1");

        verify(apiClient, never()).delete(any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/agent delete - ID 없이 호출하면 에이전트 목록에서 선택한다")
    void deleteAgentNoId() {
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.empty());

        executeCommand("delete");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/agent update - ID 없이 호출하면 에이전트 목록에서 선택한다")
    void updateAgentNoId() {
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.empty());

        executeCommand("update");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("잘못된 서브커맨드는 사용법을 안내한다")
    void invalidSubcommand() {
        executeCommand("invalid");

        assertThat(outputBuffer.toString()).contains("사용법");
    }

    @Test
    @DisplayName("/agent delete {id} - 삭제 실패 시 안내 메시지를 출력한다")
    void deleteAgentFailed() {
        ObjectNode response = createDetailResponse(1L, "test-agent", "WORKER",
                "role", "claude", "model", "key");
        when(apiClient.get("/api/v1/agents/1")).thenReturn(Optional.of(response));
        when(apiClient.delete("/api/v1/agents/1")).thenReturn(false);
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        executeCommand("delete 1");

        assertThat(outputBuffer.toString()).contains("삭제에 실패했습니다");
    }

    @Test
    @DisplayName("/agent delete {id} - 존재하지 않는 에이전트 삭제 시 안내 메시지를 출력한다")
    void deleteAgentNotFound() {
        when(apiClient.get("/api/v1/agents/999")).thenReturn(Optional.empty());

        executeCommand("delete 999");

        assertThat(outputBuffer.toString()).contains("에이전트를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/agent {id} - apiKey가 ref:secret/ 형식이면 마스킹하지 않는다")
    void showAgentDetailRefSecretNotMasked() {
        ObjectNode response = createDetailResponse(1L, "test", "WORKER",
                "role", "claude", "model", "ref:secret/my-key");
        when(apiClient.get("/api/v1/agents/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        assertThat(outputBuffer.toString()).contains("ref:secret/my-key");
    }

    @Test
    @DisplayName("/agent {id} - apiKey가 일반 문자열이면 마스킹한다")
    void showAgentDetailApiKeyMasked() {
        ObjectNode response = createDetailResponse(1L, "test", "WORKER",
                "role", "claude", "model", "sk-1234567890abcdef");
        when(apiClient.get("/api/v1/agents/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        String output = outputBuffer.toString();
        assertThat(output).doesNotContain("sk-1234567890abcdef");
        assertThat(output).contains("sk-1****cdef");
    }

    @Test
    @DisplayName("/agent {id} - 짧은 apiKey는 완전히 마스킹한다")
    void showAgentDetailShortApiKeyFullyMasked() {
        ObjectNode response = createDetailResponse(1L, "test", "WORKER",
                "role", "claude", "model", "short");
        when(apiClient.get("/api/v1/agents/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        String output = outputBuffer.toString();
        assertThat(output).contains("****");
        assertThat(output).doesNotContain("short");
    }

    @Test
    @DisplayName("/agent (빈 인자)는 list와 동일하게 동작한다")
    void emptyArgsDefaultsToList() {
        ObjectNode response = createListResponse(
                createAgent(1L, "test", "WORKER", "model")
        );
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.of(response));

        executeCommand("");

        assertThat(outputBuffer.toString()).contains("test");
    }

    private void executeCommand(String args) {
        registry.find("agent").get().executor().execute(ctx, args);
    }

    private ObjectNode createAgent(Long id, String name, String roleType, String model) {
        ObjectNode agent = mapper.createObjectNode();
        agent.put("id", id);
        agent.put("name", name);
        agent.put("roleType", roleType);
        ObjectNode llmConfig = mapper.createObjectNode();
        llmConfig.put("model", model);
        agent.set("llmConfig", llmConfig);
        return agent;
    }

    private ObjectNode createListResponse(ObjectNode... agents) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode agent : agents) {
            data.add(agent);
        }
        return response;
    }

    private ObjectNode createDetailResponse(Long id, String name, String roleType,
                                             String role, String provider, String model,
                                             String apiKey) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", id);
        data.put("name", name);
        data.put("roleType", roleType);
        data.put("role", role);
        ObjectNode llmConfig = mapper.createObjectNode();
        llmConfig.put("provider", provider);
        llmConfig.put("model", model);
        llmConfig.put("apiKey", apiKey);
        data.set("llmConfig", llmConfig);
        data.put("createdAt", "2026-02-16T10:00:00");
        data.put("updatedAt", "2026-02-16T10:00:00");
        response.set("data", data);
        return response;
    }
}
