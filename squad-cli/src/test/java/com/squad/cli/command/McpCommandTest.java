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

class McpCommandTest {

    private CommandRegistry registry;
    private SquadApiClient apiClient;
    private TableRenderer tableRenderer;
    private InteractiveFormReader formReader;
    private McpCommand mcpCommand;

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

        mcpCommand = new McpCommand(registry, apiClient, tableRenderer, formReader);

        outputBuffer = new StringWriter();
        writer = new PrintWriter(outputBuffer);
        ctx = new CommandContext(null, null, writer);
    }

    @Test
    @DisplayName("mcp 커맨드가 레지스트리에 등록된다")
    void registersMcpCommand() {
        assertThat(registry.find("mcp")).isPresent();
        assertThat(registry.find("mcp").get().description()).contains("MCP");
    }

    @Test
    @DisplayName("/mcp list - MCP 목록을 테이블로 출력한다")
    void listMcps() {
        ObjectNode response = createListResponse(
                createMcp(1L, "GitHub MCP", "GitHub 연동", "npx"),
                createMcp(2L, "Slack MCP", "Slack 연동", "node")
        );
        when(apiClient.get("/api/v1/mcps")).thenReturn(Optional.of(response));

        executeCommand("list");

        String output = outputBuffer.toString();
        assertThat(output).contains("GitHub MCP");
        assertThat(output).contains("Slack MCP");
        assertThat(output).contains("npx");
    }

    @Test
    @DisplayName("/mcp list - MCP가 없으면 안내 메시지를 출력한다")
    void listMcpsEmpty() {
        ObjectNode response = mapper.createObjectNode();
        response.putArray("data");
        when(apiClient.get("/api/v1/mcps")).thenReturn(Optional.of(response));

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("등록된 MCP가 없습니다");
    }

    @Test
    @DisplayName("/mcp list - 서버 연결 실패 시 안내 메시지를 출력한다")
    void listMcpsServerError() {
        when(apiClient.get("/api/v1/mcps")).thenReturn(Optional.empty());

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/mcp {id} - MCP 상세 정보를 출력한다")
    void showMcpDetail() {
        ObjectNode response = createDetailResponse(1L, "GitHub MCP", "GitHub 연동",
                createConfigNode("npx", "-y", "@modelcontextprotocol/server-github"));
        when(apiClient.get("/api/v1/mcps/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        String output = outputBuffer.toString();
        assertThat(output).contains("MCP 상세");
        assertThat(output).contains("GitHub MCP");
        assertThat(output).contains("GitHub 연동");
        assertThat(output).contains("npx");
    }

    @Test
    @DisplayName("/mcp {id} - 존재하지 않는 MCP 조회 시 안내 메시지를 출력한다")
    void showMcpDetailNotFound() {
        when(apiClient.get("/api/v1/mcps/999")).thenReturn(Optional.empty());

        executeCommand("999");

        assertThat(outputBuffer.toString()).contains("MCP를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/mcp create - config JSON 입력 후 MCP를 생성한다")
    void createMcp() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("GitHub MCP");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn("GitHub 연동");
        when(formReader.readJsonInput(eq(ctx), eq("config JSON 입력"), any(), any()))
                .thenReturn("{\"command\": \"npx\", \"args\": [\"-y\", \"@mcp/server-github\"]}");
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", 1);
        response.set("data", data);
        when(apiClient.post(eq("/api/v1/mcps"), any())).thenReturn(Optional.of(response));

        executeCommand("create");

        verify(apiClient).post(eq("/api/v1/mcps"), any());
        assertThat(outputBuffer.toString()).contains("MCP가 생성되었습니다");
    }

    @Test
    @DisplayName("/mcp create - 이름 입력 취소 시 생성을 중단한다")
    void createMcpCancelledAtName() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn(null);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/mcp create - 잘못된 JSON 입력 시 생성에 실패한다")
    void createMcpInvalidJson() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("Test MCP");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);
        when(formReader.readJsonInput(eq(ctx), eq("config JSON 입력"), any(), any()))
                .thenReturn("invalid json");

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("JSON 파싱 실패");
    }

    @Test
    @DisplayName("/mcp create - command 키 누락 시 생성에 실패한다")
    void createMcpMissingCommand() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("Test MCP");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);
        when(formReader.readJsonInput(eq(ctx), eq("config JSON 입력"), any(), any()))
                .thenReturn("{\"type\": \"stdio\"}");

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("'command' 키가 필요합니다");
    }

    @Test
    @DisplayName("/mcp create - config 입력을 건너뛰면 취소된다")
    void createMcpConfigSkipped() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("Test MCP");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);
        when(formReader.readJsonInput(eq(ctx), eq("config JSON 입력"), any(), any()))
                .thenReturn(null);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/mcp delete {id} - 확인 후 MCP를 삭제한다")
    void deleteMcp() {
        ObjectNode response = createDetailResponse(1L, "GitHub MCP", "GitHub 연동",
                createConfigNode("npx"));
        when(apiClient.get("/api/v1/mcps/1")).thenReturn(Optional.of(response));
        when(apiClient.delete("/api/v1/mcps/1")).thenReturn(true);
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        executeCommand("delete 1");

        verify(apiClient).delete("/api/v1/mcps/1");
        assertThat(outputBuffer.toString()).contains("MCP가 삭제되었습니다");
    }

    @Test
    @DisplayName("/mcp delete {id} - 취소하면 삭제하지 않는다")
    void deleteMcpCancelled() {
        ObjectNode response = createDetailResponse(1L, "GitHub MCP", "GitHub 연동",
                createConfigNode("npx"));
        when(apiClient.get("/api/v1/mcps/1")).thenReturn(Optional.of(response));
        when(formReader.readConfirm(any(), any())).thenReturn(false);

        executeCommand("delete 1");

        verify(apiClient, never()).delete(any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/mcp delete - ID 없이 호출하면 MCP 목록에서 선택한다")
    void deleteMcpNoId() {
        when(apiClient.get("/api/v1/mcps")).thenReturn(Optional.empty());

        executeCommand("delete");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/mcp delete {id} - 존재하지 않는 MCP 삭제 시 안내 메시지를 출력한다")
    void deleteMcpNotFound() {
        when(apiClient.get("/api/v1/mcps/999")).thenReturn(Optional.empty());

        executeCommand("delete 999");

        assertThat(outputBuffer.toString()).contains("MCP를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/mcp delete {id} - 삭제 실패 시 안내 메시지를 출력한다")
    void deleteMcpFailed() {
        ObjectNode response = createDetailResponse(1L, "GitHub MCP", "GitHub 연동",
                createConfigNode("npx"));
        when(apiClient.get("/api/v1/mcps/1")).thenReturn(Optional.of(response));
        when(apiClient.delete("/api/v1/mcps/1")).thenReturn(false);
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        executeCommand("delete 1");

        assertThat(outputBuffer.toString()).contains("삭제에 실패했습니다");
    }

    @Test
    @DisplayName("/mcp update - ID 없이 호출하면 MCP 목록에서 선택한다")
    void updateMcpNoId() {
        when(apiClient.get("/api/v1/mcps")).thenReturn(Optional.empty());

        executeCommand("update");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/mcp update {id} - 기존 config를 유지할 수 있다")
    void updateMcpKeepConfig() {
        ObjectNode response = createDetailResponse(1L, "GitHub MCP", "GitHub 연동",
                createConfigNode("npx"));
        when(apiClient.get("/api/v1/mcps/1")).thenReturn(Optional.of(response));
        when(formReader.readLine(eq(ctx), eq("이름"), eq("GitHub MCP"))).thenReturn("GitHub MCP Updated");
        when(formReader.readLine(eq(ctx), eq("설명"), eq("GitHub 연동"))).thenReturn("GitHub 연동 업데이트");
        // config 변경 안함 → false, 수정 확인 → true
        when(formReader.readConfirm(any(), eq("config를 변경하시겠습니까?"))).thenReturn(false);
        when(formReader.readConfirm(any(), eq("수정하시겠습니까?"))).thenReturn(true);

        ObjectNode updateResponse = mapper.createObjectNode();
        updateResponse.put("success", true);
        when(apiClient.put(eq("/api/v1/mcps/1"), any())).thenReturn(Optional.of(updateResponse));

        executeCommand("update 1");

        verify(apiClient).put(eq("/api/v1/mcps/1"), any());
        assertThat(outputBuffer.toString()).contains("MCP가 수정되었습니다");
    }

    @Test
    @DisplayName("잘못된 서브커맨드는 사용법을 안내한다")
    void invalidSubcommand() {
        executeCommand("invalid");

        assertThat(outputBuffer.toString()).contains("사용법");
    }

    @Test
    @DisplayName("/mcp (빈 인자)는 list와 동일하게 동작한다")
    void emptyArgsDefaultsToList() {
        ObjectNode response = createListResponse(
                createMcp(1L, "GitHub MCP", "GitHub 연동", "npx")
        );
        when(apiClient.get("/api/v1/mcps")).thenReturn(Optional.of(response));

        executeCommand("");

        assertThat(outputBuffer.toString()).contains("GitHub MCP");
    }

    @Test
    @DisplayName("/mcp create - config가 JSON 배열이면 거절한다")
    void createMcpRejectsArray() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("Test MCP");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);
        when(formReader.readJsonInput(eq(ctx), eq("config JSON 입력"), any(), any()))
                .thenReturn("[1, 2, 3]");

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("JSON 객체여야 합니다");
    }

    private void executeCommand(String args) {
        registry.find("mcp").get().executor().execute(ctx, args);
    }

    private ObjectNode createMcp(Long id, String name, String description, String command) {
        ObjectNode mcp = mapper.createObjectNode();
        mcp.put("id", id);
        mcp.put("name", name);
        mcp.put("description", description);
        ObjectNode config = mapper.createObjectNode();
        config.put("command", command);
        mcp.set("config", config);
        return mcp;
    }

    private ObjectNode createListResponse(ObjectNode... mcps) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode mcp : mcps) {
            data.add(mcp);
        }
        return response;
    }

    private ObjectNode createDetailResponse(Long id, String name, String description,
                                             ObjectNode config) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", id);
        data.put("name", name);
        data.put("description", description);
        data.set("config", config);
        data.put("createdAt", "2026-02-16T10:00:00");
        data.put("updatedAt", "2026-02-16T10:00:00");
        response.set("data", data);
        return response;
    }

    private ObjectNode createConfigNode(String command, String... args) {
        ObjectNode config = mapper.createObjectNode();
        config.put("command", command);
        if (args.length > 0) {
            ArrayNode argsNode = config.putArray("args");
            for (String arg : args) {
                argsNode.add(arg);
            }
        }
        return config;
    }
}
