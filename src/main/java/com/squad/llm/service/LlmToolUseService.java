package com.squad.llm.service;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.ValidationException;
import com.squad.llm.LlmProvider;
import com.squad.llm.LlmProviderFactory;
import com.squad.llm.model.LlmMessage;
import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM의 tool_use 응답을 감지하고 도구 실행 후 재호출하는 서비스.
 *
 * <p>LLM 응답에 toolCalls가 포함되어 있으면 {@link LlmToolExecutor}로 각 도구를
 * 실행하고, 결과를 포함하여 LLM을 재호출한다. LLM이 연쇄적으로 도구를 호출할 수
 * 있으므로 while 루프로 처리하며, 무한 루프 방지를 위해 최대 반복 횟수를 제한한다.</p>
 *
 * @see LlmToolExecutor
 */
@Slf4j
@Service
public class LlmToolUseService {

    private static final int MAX_TOOL_USE_ITERATIONS = 10;

    private final LlmProviderFactory providerFactory;
    private final LlmToolExecutor toolExecutor;

    /**
     * {@code LlmToolUseService}를 생성한다.
     *
     * @param providerFactory LLM Provider 팩토리
     * @param toolExecutor    도구 실행기
     */
    public LlmToolUseService(LlmProviderFactory providerFactory, LlmToolExecutor toolExecutor) {
        this.providerFactory = providerFactory;
        this.toolExecutor = toolExecutor;
    }

    /**
     * LLM에 메시지를 전송하고 tool_use 응답을 자동으로 처리한다.
     *
     * <p>LLM이 도구 호출을 요청하면 도구를 실행하고 결과를 포함하여
     * 재호출한다. 이 과정을 최대 {@value #MAX_TOOL_USE_ITERATIONS}회까지 반복한다.</p>
     *
     * @param providerName LLM Provider 이름
     * @param request      LLM 요청
     * @return 최종 LLM 응답
     */
    public LlmResponse sendWithToolUse(String providerName, LlmRequest request) {
        LlmProvider provider = providerFactory.getProvider(providerName)
                .orElseThrow(() -> new ValidationException(
                        ErrorCode.INVALID_REQUEST,
                        "지원하지 않는 provider입니다."
                ));

        LlmResponse response = provider.sendMessage(request);
        List<LlmMessage> accumulatedMessages = new ArrayList<>();
        if (request.messages() != null) {
            accumulatedMessages.addAll(request.messages());
        }

        int iteration = 0;
        while (hasToolCalls(response) && iteration < MAX_TOOL_USE_ITERATIONS) {
            iteration++;
            log.debug("tool_use 반복 처리: iteration={}, toolCallCount={}",
                    iteration, response.toolCalls().size());

            List<LlmToolResult> results = response.toolCalls().stream()
                    .map(toolExecutor::execute)
                    .toList();

            for (LlmToolResult result : results) {
                accumulatedMessages.add(new LlmMessage("user", formatToolResult(result)));
            }

            LlmRequest followUp = new LlmRequest(
                    request.model(),
                    request.systemPrompt(),
                    List.copyOf(accumulatedMessages),
                    request.maxTokens(),
                    request.temperature(),
                    request.tools()
            );

            response = provider.sendMessage(followUp);
        }

        if (hasToolCalls(response)) {
            log.warn("tool_use 최대 반복 횟수 초과: maxIterations={}", MAX_TOOL_USE_ITERATIONS);
        }

        return response;
    }

    private boolean hasToolCalls(LlmResponse response) {
        return response.toolCalls() != null && !response.toolCalls().isEmpty();
    }

    private String formatToolResult(LlmToolResult result) {
        return String.format(
                "tool_result id=%s name=%s output=%s",
                result.toolCallId(),
                result.name(),
                result.output()
        );
    }
}
