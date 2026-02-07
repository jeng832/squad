package com.squad.llm.model;

/**
 * LLM에 전달하는 대화 메시지 단위.
 */
public record LlmMessage(
        String role,
        String content
) {
}
