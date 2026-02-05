package com.squad.mcp.dto;

import com.squad.mcp.domain.Mcp;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * MCP 응답 DTO.
 */
public record McpResponse(
        Long id,
        String name,
        String description,
        Map<String, Object> config,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static McpResponse from(Mcp mcp) {
        return new McpResponse(
                mcp.getId(),
                mcp.getName(),
                mcp.getDescription(),
                mcp.getConfig(),
                mcp.getCreatedAt(),
                mcp.getUpdatedAt()
        );
    }
}
