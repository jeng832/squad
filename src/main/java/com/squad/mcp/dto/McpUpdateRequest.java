package com.squad.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * MCP 수정 요청 DTO.
 */
public record McpUpdateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,

        @NotNull
        Map<String, Object> config
) {
}
