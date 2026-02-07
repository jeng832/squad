package com.squad.llm;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * provider 이름에 따라 적절한 LlmProvider 구현을 반환하는 팩토리.
 */
@Component
public class LlmProviderFactory {

    private final Map<String, LlmProvider> providersByName;

    public LlmProviderFactory(java.util.List<LlmProvider> providers) {
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProvider::getProviderName, Function.identity()));
    }

    public LlmProvider getProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "LLM provider 이름이 비어 있습니다.");
        }

        LlmProvider provider = providersByName.get(providerName);
        if (provider == null) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "지원하지 않는 LLM provider: " + providerName);
        }
        return provider;
    }
}
