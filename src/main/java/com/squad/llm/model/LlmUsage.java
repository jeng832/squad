package com.squad.llm.model;

/**
 * LLM 호출의 토큰 사용량.
 *
 * <p>{@code inputTokens}는 프롬프트/컨텍스트에 사용된 토큰 수,
 * {@code outputTokens}는 응답에 사용된 토큰 수입니다.</p>
 */
public record LlmUsage(
        Integer inputTokens,
        Integer outputTokens
) {
}
