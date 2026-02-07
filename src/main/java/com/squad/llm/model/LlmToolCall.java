package com.squad.llm.model;

import java.util.Map;

/**
 * LLM 응답 내 Tool 호출 요청 정보.
 *
 * <p>{@code id}는 호출 식별자이며, 후속 tool_result 매칭에 사용됩니다.</p>
 * <p>{@code name}은 호출할 Tool 이름입니다.</p>
 * <p>{@code arguments}가 실제 입력값이며, JSON 객체 형태로 전달됩니다.</p>
 */
public record LlmToolCall(
        String id,
        String name,
        Map<String, Object> arguments
) {
}
