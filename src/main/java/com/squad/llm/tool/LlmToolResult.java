package com.squad.llm.tool;

/**
 * LLM tool 실행 결과.
 */
public record LlmToolResult(
        String toolCallId,
        String name,
        String output
) {
}
