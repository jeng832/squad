package com.squad.llm.model;

/**
 * LLM에 전달하는 대화 메시지 단위.
 *
 * <p>{@code role}은 일반적으로 {@code system}/{@code user}/{@code assistant} 중 하나이며,
 * {@code content}는 해당 역할의 발화 텍스트를 의미합니다.</p>
 */
public record LlmMessage(
        String role,
        String content
) {
}
