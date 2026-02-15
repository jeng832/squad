package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;

/**
 * Built-in Tool 단위 실행 계약.
 */
public interface BuiltInToolCommand {

    LlmTool definition();

    default String toolName() {
        return definition().name();
    }

    String execute(LlmToolCall call, BuiltInToolContext context) throws Exception;
}
