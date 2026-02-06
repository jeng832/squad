package com.squad.mcp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.mcp.dto.McpCreateRequest;
import com.squad.mcp.dto.McpResponse;
import com.squad.mcp.dto.McpUpdateRequest;
import com.squad.mcp.service.McpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(McpController.class)
class McpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private McpService mcpService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private McpResponse sampleResponse() {
        return new McpResponse(
                1L,
                "github-mcp",
                "GitHub 저장소 관리 MCP",
                Map.of("command", "npx", "args", List.of("-y", "@modelcontextprotocol/server-github")),
                NOW,
                NOW
        );
    }

    @Test
    void MCP_목록_조회_시_성공_응답_반환() throws Exception {
        given(mcpService.findAll()).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/mcps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("github-mcp"));
    }

    @Test
    void MCP_ID로_조회_시_성공_응답_반환() throws Exception {
        given(mcpService.findById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/mcps/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("github-mcp"));
    }

    @Test
    void 존재하지_않는_MCP_조회_시_404_응답() throws Exception {
        given(mcpService.findById(99L)).willThrow(new NotFoundException(ErrorCode.MCP_NOT_FOUND));

        mockMvc.perform(get("/api/v1/mcps/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MCP_NOT_FOUND"));
    }

    @Test
    void MCP_생성_시_201_응답_반환() throws Exception {
        McpCreateRequest request = new McpCreateRequest(
                "github-mcp",
                "GitHub 저장소 관리 MCP",
                Map.of("command", "npx", "args", List.of("-y", "@modelcontextprotocol/server-github"))
        );
        given(mcpService.create(any(McpCreateRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/mcps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.message").value("MCP가 생성되었습니다."));
    }

    @Test
    void MCP_생성_시_name_빈_값_400_응답() throws Exception {
        McpCreateRequest request = new McpCreateRequest(
                "",
                "설명",
                Map.of("command", "npx")
        );

        mockMvc.perform(post("/api/v1/mcps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void MCP_생성_시_config_command_누락_400_응답() throws Exception {
        McpCreateRequest request = new McpCreateRequest(
                "test-mcp",
                "설명",
                Map.of("args", List.of("-y", "some-package"))
        );
        given(mcpService.create(any(McpCreateRequest.class)))
                .willThrow(new ValidationException(ErrorCode.VALIDATION_FAILED, "config에 'command' 키가 필수입니다."));

        mockMvc.perform(post("/api/v1/mcps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void MCP_생성_시_config_null_400_응답() throws Exception {
        // config를 null로 설정하기 위해 raw map 사용
        Map<String, Object> body = Map.of(
                "name", "test-mcp"
        );

        mockMvc.perform(post("/api/v1/mcps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void MCP_수정_시_성공_응답_반환() throws Exception {
        McpUpdateRequest request = new McpUpdateRequest(
                "github-mcp-updated",
                "수정된 설명",
                Map.of("command", "npx", "args", List.of("-y", "@modelcontextprotocol/server-github"))
        );
        McpResponse response = new McpResponse(1L, request.name(), request.description(), request.config(), NOW, NOW);
        given(mcpService.update(eq(1L), any(McpUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/v1/mcps/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("github-mcp-updated"));
    }

    @Test
    void 존재하지_않는_MCP_수정_시_404_응답() throws Exception {
        McpUpdateRequest request = new McpUpdateRequest(
                "name",
                "desc",
                Map.of("command", "npx")
        );
        given(mcpService.update(eq(99L), any(McpUpdateRequest.class)))
                .willThrow(new NotFoundException(ErrorCode.MCP_NOT_FOUND));

        mockMvc.perform(put("/api/v1/mcps/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MCP_NOT_FOUND"));
    }

    @Test
    void MCP_수정_시_config_command_누락_400_응답() throws Exception {
        McpUpdateRequest request = new McpUpdateRequest(
                "test-mcp",
                "설명",
                Map.of("args", List.of("-y", "some-package"))
        );
        given(mcpService.update(eq(1L), any(McpUpdateRequest.class)))
                .willThrow(new ValidationException(ErrorCode.VALIDATION_FAILED, "config에 'command' 키가 필수입니다."));

        mockMvc.perform(put("/api/v1/mcps/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void MCP_삭제_시_성공_응답_반환() throws Exception {
        willDoNothing().given(mcpService).delete(1L);

        mockMvc.perform(delete("/api/v1/mcps/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("MCP가 삭제되었습니다."));
    }

    @Test
    void 존재하지_않는_MCP_삭제_시_404_응답() throws Exception {
        willThrow(new NotFoundException(ErrorCode.MCP_NOT_FOUND)).given(mcpService).delete(99L);

        mockMvc.perform(delete("/api/v1/mcps/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("MCP_NOT_FOUND"));
    }
}
