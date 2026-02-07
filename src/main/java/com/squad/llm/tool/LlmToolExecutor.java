package com.squad.llm.tool;

import com.squad.llm.model.LlmToolCall;

/**
 * Tool 호출을 실제로 실행하는 Executor.
 */
public interface LlmToolExecutor {

    LlmToolResult execute(LlmToolCall toolCall);
}
