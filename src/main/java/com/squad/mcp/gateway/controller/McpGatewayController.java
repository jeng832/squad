package com.squad.mcp.gateway.controller;

import com.squad.common.api.ApiResponse;
import com.squad.llm.model.LlmTool;
import com.squad.llm.tool.LlmToolResult;
import com.squad.mcp.gateway.McpGatewayService;
import com.squad.mcp.gateway.dto.McpGatewayEvent;
import com.squad.mcp.gateway.dto.McpToolCallRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Agent Runtime이 사용하는 MCP Gateway HTTP/SSE 엔드포인트.
 */
@RestController
@RequestMapping("/api/v1/mcp-gateway")
public class McpGatewayController {

    private final McpGatewayService mcpGatewayService;

    public McpGatewayController(McpGatewayService mcpGatewayService) {
        this.mcpGatewayService = mcpGatewayService;
    }

    @PostMapping("/mcps/{mcpName}/register")
    public ApiResponse<List<LlmTool>> register(@PathVariable String mcpName) {
        return ApiResponse.success(mcpGatewayService.register(mcpName));
    }

    @DeleteMapping("/mcps/{mcpName}")
    public ApiResponse<Object> unregister(@PathVariable String mcpName) {
        mcpGatewayService.unregister(mcpName);
        return ApiResponse.success(null, "MCP가 해제되었습니다.");
    }

    @GetMapping("/tools")
    public ApiResponse<List<LlmTool>> getTools(@RequestParam(required = false) String mcpName) {
        return ApiResponse.success(mcpGatewayService.getTools(mcpName));
    }

    @PostMapping("/tools/call")
    public ApiResponse<LlmToolResult> callTool(@Valid @RequestBody McpToolCallRequest request) {
        return ApiResponse.success(mcpGatewayService.callTool(request.toolAlias(), request.arguments()));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<McpGatewayEvent>> events() {
        return mcpGatewayService.streamEvents();
    }
}
