package com.squad.mcp.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.llm.model.LlmTool;
import com.squad.llm.tool.LlmToolResult;
import com.squad.mcp.gateway.McpGatewayService;
import com.squad.mcp.gateway.dto.McpGatewayEvent;
import com.squad.mcp.gateway.dto.McpToolCallRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(McpGatewayController.class)
@DisplayName("McpGatewayController 통합 테스트")
class McpGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private McpGatewayService mcpGatewayService;

    @Test
    @DisplayName("MCP 등록 성공 시 도구 목록을 반환한다")
    void registerSuccess() throws Exception {
        LlmTool tool = new LlmTool("test-mcp__read", "파일 읽기", Map.of("type", "object"));
        given(mcpGatewayService.register("test-mcp")).willReturn(List.of(tool));

        mockMvc.perform(post("/api/v1/mcp-gateway/mcps/test-mcp/register"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("test-mcp__read"));
    }

    @Test
    @DisplayName("존재하지 않는 MCP 등록 시 404 응답")
    void registerNotFound() throws Exception {
        given(mcpGatewayService.register("unknown"))
                .willThrow(new NotFoundException(ErrorCode.MCP_NOT_FOUND));

        mockMvc.perform(post("/api/v1/mcp-gateway/mcps/unknown/register"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MCP_NOT_FOUND"));
    }

    @Test
    @DisplayName("MCP 해제 성공 시 200 응답")
    void unregisterSuccess() throws Exception {
        willDoNothing().given(mcpGatewayService).unregister("test-mcp");

        mockMvc.perform(delete("/api/v1/mcp-gateway/mcps/test-mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("MCP가 해제되었습니다."));
    }

    @Test
    @DisplayName("전체 도구 목록 조회 시 성공 응답")
    void getToolsAll() throws Exception {
        LlmTool tool = new LlmTool("mcp__tool1", "도구1", Map.of());
        given(mcpGatewayService.getTools(null)).willReturn(List.of(tool));

        mockMvc.perform(get("/api/v1/mcp-gateway/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("mcp__tool1"));
    }

    @Test
    @DisplayName("mcpName 필터로 도구 목록 조회 시 성공 응답")
    void getToolsByMcpName() throws Exception {
        LlmTool tool = new LlmTool("my-mcp__tool1", "도구1", Map.of());
        given(mcpGatewayService.getTools("my-mcp")).willReturn(List.of(tool));

        mockMvc.perform(get("/api/v1/mcp-gateway/tools").param("mcpName", "my-mcp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("my-mcp__tool1"));
    }

    @Test
    @DisplayName("도구 호출 성공 시 결과를 반환한다")
    void callToolSuccess() throws Exception {
        McpToolCallRequest request = new McpToolCallRequest("mcp__tool1", Map.of("path", "/test"));
        LlmToolResult result = new LlmToolResult("call-1", "mcp__tool1", "파일 내용");
        given(mcpGatewayService.callTool(eq("mcp__tool1"), any())).willReturn(result);

        mockMvc.perform(post("/api/v1/mcp-gateway/tools/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.output").value("파일 내용"));
    }

    @Test
    @DisplayName("도구 호출 시 toolAlias가 빈 값이면 400 응답")
    void callToolValidationFailed() throws Exception {
        Map<String, Object> body = Map.of("toolAlias", "", "arguments", Map.of());

        mockMvc.perform(post("/api/v1/mcp-gateway/tools/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
