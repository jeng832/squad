package com.squad.llm.model;

import java.util.Map;

/**
 * LLM에 전달하는 Tool 정의 (Function/Tool Use 공통).
 *
 * <p>{@code inputSchema}는 JSON Schema이며, LLM이 tool_use 입력 형식을
 * 이해하도록 하기 위한 선언입니다.</p>
 */
public record LlmTool(
        String name,
        String description,
        Map<String, Object> inputSchema
) {
}
