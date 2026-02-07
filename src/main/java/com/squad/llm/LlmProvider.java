package com.squad.llm;

import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;

/**
 * 여러 LLM 벤더를 공통 인터페이스로 추상화하는 Provider.
 */
public interface LlmProvider {

    /**
     * 제공자 식별자 (예: "claude", "openai", "gemini").
     */
    String getProviderName();

    /**
     * 단일 요청/응답 기반 메시지 호출.
     */
    LlmResponse sendMessage(LlmRequest request);
}
