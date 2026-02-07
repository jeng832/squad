package com.squad.llm.model;

import java.util.List;

/**
 * LLM 호출 공통 응답 모델.
 *
 * <p>{@code content}는 텍스트 응답(여러 블록인 경우 병합된 결과)입니다.</p>
 * <p>{@code toolCalls}는 LLM이 tool_use를 요청했을 때의 호출 목록이며,
 * 비어 있으면 일반 텍스트 응답으로 처리합니다.</p>
 */
public record LlmResponse(
        String id,
        String content,
        String finishReason,
        List<LlmToolCall> toolCalls,
        LlmUsage usage
) {
}
