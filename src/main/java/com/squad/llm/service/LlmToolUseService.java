package com.squad.llm.service;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.ValidationException;
import com.squad.llm.LlmProvider;
import com.squad.llm.LlmProviderFactory;
import com.squad.llm.model.LlmMessage;
import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LlmToolUseService {

    private final LlmProviderFactory providerFactory;
    private final LlmToolExecutor toolExecutor;

    public LlmToolUseService(LlmProviderFactory providerFactory, LlmToolExecutor toolExecutor) {
        this.providerFactory = providerFactory;
        this.toolExecutor = toolExecutor;
    }

    public LlmResponse sendWithToolUse(String providerName, LlmRequest request) {
        LlmProvider provider = providerFactory.getProvider(providerName)
                .orElseThrow(() -> new ValidationException(
                        ErrorCode.INVALID_REQUEST,
                        "지원하지 않는 provider입니다."
                ));

        LlmResponse firstResponse = provider.sendMessage(request);
        if (firstResponse.toolCalls() == null || firstResponse.toolCalls().isEmpty()) {
            return firstResponse;
        }

        List<LlmToolResult> results = firstResponse.toolCalls().stream()
                .map(toolExecutor::execute)
                .toList();

        LlmRequest followUp = buildFollowUpRequest(request, results);
        return provider.sendMessage(followUp);
    }

    private LlmRequest buildFollowUpRequest(LlmRequest original, List<LlmToolResult> results) {
        List<LlmMessage> messages = new ArrayList<>();
        if (original.messages() != null) {
            messages.addAll(original.messages());
        }
        for (LlmToolResult result : results) {
            messages.add(new LlmMessage("user", formatToolResult(result)));
        }

        return new LlmRequest(
                original.model(),
                original.systemPrompt(),
                messages,
                original.maxTokens(),
                original.temperature(),
                original.tools()
        );
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
