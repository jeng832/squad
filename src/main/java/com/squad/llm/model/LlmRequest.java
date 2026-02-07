package com.squad.llm.model;

import java.util.List;

/**
 * LLM 호출 공통 요청 모델.
 *
 * <p>{@code model}이 null이면 Provider 기본값을 사용합니다.</p>
 * <p>{@code systemPrompt}는 시스템 메시지 역할을 하며, provider에 따라 별도 필드로 전달됩니다.</p>
 * <p>{@code messages}는 대화 히스토리이며, tool_use 결과를 추가해 재호출할 때도 사용됩니다.</p>
 * <p>{@code tools}는 LLM에게 사용 가능한 Tool 목록을 선언하기 위한 정의입니다.</p>
 */
public record LlmRequest(
        String model,
        String systemPrompt,
        List<LlmMessage> messages,
        Integer maxTokens,
        Double temperature,
        List<LlmTool> tools
) {
}
