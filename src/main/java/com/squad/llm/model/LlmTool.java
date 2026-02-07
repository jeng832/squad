package com.squad.llm.model;

import java.util.Map;

/**
 * LLM에 전달하는 Tool 정의 (Function/Tool Use 공통).
 */
public record LlmTool(
        String name,
        String description,
        Map<String, Object> inputSchema
) {
}
