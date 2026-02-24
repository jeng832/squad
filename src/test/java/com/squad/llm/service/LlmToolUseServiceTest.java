package com.squad.llm.service;

import com.squad.llm.LlmProvider;
import com.squad.llm.LlmProviderFactory;
import com.squad.llm.model.LlmMessage;
import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.squad.common.exception.ValidationException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LlmToolUseService")
class LlmToolUseServiceTest {

    @Test
    @DisplayName("tool_use 없으면 단일 호출로 응답을 반환한다")
    void returnsResponseWithoutToolUse() {
        StubProvider provider = new StubProvider("claude",
                List.of(new LlmResponse("id", "ok", "end_turn", List.of(), null)));

        LlmToolUseService service = new LlmToolUseService(
                new LlmProviderFactory(List.of(provider)),
                toolCall -> new LlmToolResult(toolCall.id(), toolCall.name(), "out")
        );

        LlmRequest request = new LlmRequest(
                null, "sys",
                List.of(new LlmMessage("user", "hi")),
                null, null, null, null
        );

        LlmResponse response = service.sendWithToolUse("claude", request);

        assertThat(response.content()).isEqualTo("ok");
        assertThat(provider.requests()).hasSize(1);
    }

    @Test
    @DisplayName("tool_use 있으면 도구 결과를 추가해 재호출한다")
    void executesToolAndRecalls() {
        LlmToolCall call = new LlmToolCall("call-1", "do_something", Map.of("x", 1));
        StubProvider provider = new StubProvider("claude", List.of(
                new LlmResponse("id1", "", "tool_use", List.of(call), null),
                new LlmResponse("id2", "done", "end_turn", List.of(), null)
        ));

        LlmToolExecutor executor = toolCall ->
                new LlmToolResult(toolCall.id(), toolCall.name(), "result-1");
        LlmToolUseService service = new LlmToolUseService(
                new LlmProviderFactory(List.of(provider)), executor);

        LlmRequest request = new LlmRequest(
                null, "sys",
                List.of(new LlmMessage("user", "hi")),
                null, null, null, null
        );

        LlmResponse response = service.sendWithToolUse("claude", request);

        assertThat(response.content()).isEqualTo("done");
        assertThat(provider.requests()).hasSize(2);
        List<LlmMessage> followUpMessages = provider.requests().get(1).messages();
        assertThat(followUpMessages.get(followUpMessages.size() - 1).content())
                .contains("tool_result id=call-1 name=do_something output=result-1");
    }

    @Test
    @DisplayName("연쇄적으로 tool_use가 발생하면 반복 처리한다")
    void handlesChainedToolUse() {
        LlmToolCall call1 = new LlmToolCall("call-1", "step1", Map.of());
        LlmToolCall call2 = new LlmToolCall("call-2", "step2", Map.of());
        StubProvider provider = new StubProvider("claude", List.of(
                new LlmResponse("id1", "", "tool_use", List.of(call1), null),
                new LlmResponse("id2", "", "tool_use", List.of(call2), null),
                new LlmResponse("id3", "최종 결과", "end_turn", List.of(), null)
        ));

        LlmToolExecutor executor = toolCall ->
                new LlmToolResult(toolCall.id(), toolCall.name(), toolCall.name() + "-output");
        LlmToolUseService service = new LlmToolUseService(
                new LlmProviderFactory(List.of(provider)), executor);

        LlmRequest request = new LlmRequest(
                null, "sys",
                List.of(new LlmMessage("user", "hi")),
                null, null, null, null
        );

        LlmResponse response = service.sendWithToolUse("claude", request);

        assertThat(response.content()).isEqualTo("최종 결과");
        assertThat(provider.requests()).hasSize(3);
    }

    @Test
    @DisplayName("최대 반복 횟수를 초과하면 마지막 응답을 반환한다")
    void stopsAtMaxIterations() {
        LlmToolCall call = new LlmToolCall("call-loop", "infinite", Map.of());
        // 11개 응답: tool_use 10번 + 마지막 1번 (실제로는 10번째에서 멈추므로 11번째 사용 안됨)
        List<LlmResponse> responses = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            responses.add(new LlmResponse("id" + i, "", "tool_use", List.of(call), null));
        }
        StubProvider provider = new StubProvider("claude", responses);

        LlmToolExecutor executor = toolCall ->
                new LlmToolResult(toolCall.id(), toolCall.name(), "loop-result");
        LlmToolUseService service = new LlmToolUseService(
                new LlmProviderFactory(List.of(provider)), executor);

        LlmRequest request = new LlmRequest(
                null, "sys",
                List.of(new LlmMessage("user", "hi")),
                null, null, null, null
        );

        LlmResponse response = service.sendWithToolUse("claude", request);

        // 1 (초기) + 10 (루프) = 11번 호출
        assertThat(provider.requests()).hasSize(11);
    }

    @Test
    @DisplayName("메시지가 루프 동안 누적된다")
    void accumulatesMessagesDuringLoop() {
        LlmToolCall call1 = new LlmToolCall("c1", "tool1", Map.of());
        LlmToolCall call2 = new LlmToolCall("c2", "tool2", Map.of());
        StubProvider provider = new StubProvider("claude", List.of(
                new LlmResponse("id1", "", "tool_use", List.of(call1), null),
                new LlmResponse("id2", "", "tool_use", List.of(call2), null),
                new LlmResponse("id3", "끝", "end_turn", List.of(), null)
        ));

        LlmToolExecutor executor = toolCall ->
                new LlmToolResult(toolCall.id(), toolCall.name(), toolCall.name() + "-out");
        LlmToolUseService service = new LlmToolUseService(
                new LlmProviderFactory(List.of(provider)), executor);

        LlmRequest request = new LlmRequest(
                null, "sys",
                List.of(new LlmMessage("user", "시작")),
                null, null, null, null
        );

        service.sendWithToolUse("claude", request);

        // 3번째 호출의 메시지: 원본(1) + tool_result(1) + tool_result(1) = 3
        LlmRequest thirdRequest = provider.requests().get(2);
        assertThat(thirdRequest.messages()).hasSize(3);
        assertThat(thirdRequest.messages().get(0).content()).isEqualTo("시작");
        assertThat(thirdRequest.messages().get(1).content()).contains("tool1-out");
        assertThat(thirdRequest.messages().get(2).content()).contains("tool2-out");
    }

    @Test
    @DisplayName("지원하지 않는 provider이면 ValidationException 발생")
    void unsupportedProviderThrows() {
        StubProvider provider = new StubProvider("claude", List.of());

        LlmToolUseService service = new LlmToolUseService(
                new LlmProviderFactory(List.of(provider)),
                toolCall -> new LlmToolResult(toolCall.id(), toolCall.name(), "out")
        );

        LlmRequest request = new LlmRequest(
                null, "sys",
                List.of(new LlmMessage("user", "hi")),
                null, null, null, null
        );

        assertThatThrownBy(() -> service.sendWithToolUse("unknown-provider", request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("지원하지 않는 provider");
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
