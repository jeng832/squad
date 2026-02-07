package com.squad.llm;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * provider 이름에 따라 적절한 LlmProvider 구현을 반환하는 팩토리.
 */
@Service
public class LlmProviderFactory {

    private final Map<String, LlmProvider> providersByName;

    public LlmProviderFactory(java.util.List<LlmProvider> providers) {
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LlmProvider::getProviderName, Function.identity()));
    }

    public Optional<LlmProvider> getProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(providersByName.get(providerName));
    }
}
