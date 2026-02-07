package com.squad.llm.service;

import com.squad.llm.LlmProvider;
import com.squad.llm.LlmProviderFactory;
import com.squad.llm.model.LlmMessage;
import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmToolUseServiceTest {

    @Test
    void tool_use_없으면_단일_호출() {
        StubProvider provider = new StubProvider("claude",
                List.of(new LlmResponse("id", "ok", "end_turn", List.of(), null)));

        LlmToolUseService service = new LlmToolUseService(
                new LlmProviderFactory(List.of(provider)),
                toolCall -> new LlmToolResult(toolCall.id(), toolCall.name(), "out")
        );

        LlmRequest request = new LlmRequest(
                null,
                "sys",
                List.of(new LlmMessage("user", "hi")),
                null,
                null,
                null
        );

        LlmResponse response = service.sendWithToolUse("claude", request);

        assertThat(response.content()).isEqualTo("ok");
        assertThat(provider.requests()).hasSize(1);
    }

    @Test
    void tool_use_있으면_결과를_추가해_재호출() {
        LlmToolCall call = new LlmToolCall("call-1", "do_something", Map.of("x", 1));
        StubProvider provider = new StubProvider("claude", List.of(
                new LlmResponse("id1", "", "tool_use", List.of(call), null),
                new LlmResponse("id2", "done", "end_turn", List.of(), null)
        ));

        LlmToolExecutor executor = toolCall -> new LlmToolResult(toolCall.id(), toolCall.name(), "result-1");
        LlmToolUseService service = new LlmToolUseService(new LlmProviderFactory(List.of(provider)), executor);

        LlmRequest request = new LlmRequest(
                null,
                "sys",
                List.of(new LlmMessage("user", "hi")),
                null,
                null,
                null
        );

        LlmResponse response = service.sendWithToolUse("claude", request);

        assertThat(response.content()).isEqualTo("done");
        assertThat(provider.requests()).hasSize(2);
        List<LlmMessage> followUpMessages = provider.requests().get(1).messages();
        assertThat(followUpMessages.get(followUpMessages.size() - 1).content())
                .contains("tool_result id=call-1 name=do_something output=result-1");
    }

    private static final class StubProvider implements LlmProvider {

        private final String name;
        private final Deque<LlmResponse> responses;
        private final List<LlmRequest> requests = new ArrayList<>();

        private StubProvider(String name, List<LlmResponse> responses) {
            this.name = name;
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public String getProviderName() {
            return name;
        }

        @Override
        public LlmResponse sendMessage(LlmRequest request) {
            requests.add(request);
            return responses.removeFirst();
        }

        List<LlmRequest> requests() {
            return requests;
        }
    }
}
