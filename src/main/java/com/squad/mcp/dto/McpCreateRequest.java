package com.squad.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * MCP 생성 요청 DTO.
 *
 * <p>config는 최소한 {@code command} 키를 포함해야 합니다.</p>
 */
public record McpCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,

        @NotNull
        Map<String, Object> config
) {
}
