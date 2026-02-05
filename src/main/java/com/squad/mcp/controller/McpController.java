package com.squad.mcp.controller;

import com.squad.common.api.ApiResponse;
import com.squad.mcp.dto.McpCreateRequest;
import com.squad.mcp.dto.McpResponse;
import com.squad.mcp.dto.McpUpdateRequest;
import com.squad.mcp.service.McpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mcps")
public class McpController {

    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @GetMapping
    public ApiResponse<List<McpResponse>> findAll() {
        return ApiResponse.success(mcpService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<McpResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(mcpService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<McpResponse>> create(@Valid @RequestBody McpCreateRequest request) {
        McpResponse response = mcpService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "MCP가 생성되었습니다."));
    }

    @PutMapping("/{id}")
    public ApiResponse<McpResponse> update(@PathVariable Long id, @Valid @RequestBody McpUpdateRequest request) {
        return ApiResponse.success(mcpService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        mcpService.delete(id);
        return ApiResponse.success(null, "MCP가 삭제되었습니다.");
    }
}
