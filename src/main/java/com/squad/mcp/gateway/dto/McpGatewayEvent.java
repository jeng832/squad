package com.squad.mcp.gateway.dto;

import java.time.LocalDateTime;

/**
 * MCP Gateway 이벤트 DTO.
 */
public record McpGatewayEvent(
        String type,
        String mcpName,
        String toolAlias,
        String message,
        LocalDateTime timestamp
) {
}
