package com.squad.llm;

import com.squad.common.exception.ValidationException;
import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmProviderFactoryTest {

    @Test
    void providerName으로_Provider_조회() {
        DummyProvider claude = new DummyProvider("claude");
        DummyProvider openai = new DummyProvider("openai");
        LlmProviderFactory factory = new LlmProviderFactory(List.of(claude, openai));

        LlmProvider provider = factory.getProvider("openai");

        assertThat(provider).isSameAs(openai);
    }

    @Test
    void 지원하지_않는_provider_요청시_ValidationException() {
        LlmProviderFactory factory = new LlmProviderFactory(List.of(new DummyProvider("claude")));

        assertThatThrownBy(() -> factory.getProvider("gemini"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("지원하지 않는 LLM provider");
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
