package com.squad.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP 서버가 제공하는 도구의 정보.
 *
 * <p>tools/list 응답에서 파싱된 개별 도구의 이름, 설명, 입력 스키마를 담는다.</p>
 *
 * @param name        도구 이름
 * @param description 도구 설명
 * @param inputSchema 도구 입력 JSON Schema
 */
public record McpToolInfo(
        String name,
        String description,
        JsonNode inputSchema
) {
}
