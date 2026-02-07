package com.squad.llm.model;

import java.util.List;

/**
 * LLM 호출 공통 응답 모델.
 */
public record LlmResponse(
        String id,
        String content,
        String finishReason,
        List<LlmToolCall> toolCalls,
        LlmUsage usage
) {
}
