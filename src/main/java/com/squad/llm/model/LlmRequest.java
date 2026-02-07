package com.squad.llm.model;

import java.util.List;

/**
 * LLM 호출 공통 요청 모델.
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
