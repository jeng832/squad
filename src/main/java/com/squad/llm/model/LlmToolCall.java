package com.squad.llm.model;

import java.util.Map;

/**
 * LLM 응답 내 Tool 호출 정보.
 */
public record LlmToolCall(
        String id,
        String name,
        Map<String, Object> arguments
) {
}
