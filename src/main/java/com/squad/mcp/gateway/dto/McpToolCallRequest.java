package com.squad.mcp.gateway.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * MCP Gateway tool 호출 요청 DTO.
 */
public record McpToolCallRequest(
        @NotBlank String toolAlias,
        Map<String, Object> arguments
) {
}
