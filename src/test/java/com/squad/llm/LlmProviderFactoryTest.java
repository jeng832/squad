package com.squad.llm;

import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderFactoryTest {

    @Test
    void providerName으로_Provider_조회() {
        DummyProvider claude = new DummyProvider("claude");
        DummyProvider openai = new DummyProvider("openai");
        LlmProviderFactory factory = new LlmProviderFactory(List.of(claude, openai));

        Optional<LlmProvider> provider = factory.getProvider("openai");

        assertThat(provider).containsSame(openai);
    }

    @Test
    void 지원하지_않는_provider_요청시_empty_반환() {
        LlmProviderFactory factory = new LlmProviderFactory(List.of(new DummyProvider("claude")));

        assertThat(factory.getProvider("gemini")).isEmpty();
        assertThat(factory.getProvider(null)).isEmpty();
        assertThat(factory.getProvider("")).isEmpty();
    }

    private static class DummyProvider implements LlmProvider {
        private final String name;

        DummyProvider(String name) {
            this.name = name;
        }

        @Override
        public String getProviderName() {
            return name;
        }

        @Override
        public LlmResponse sendMessage(LlmRequest request) {
            return null;
        }
    }
}
