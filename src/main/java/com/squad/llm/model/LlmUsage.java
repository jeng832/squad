package com.squad.llm.model;

/**
 * LLM 호출의 토큰 사용량.
 */
public record LlmUsage(
        Integer inputTokens,
        Integer outputTokens
) {
}
