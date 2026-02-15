package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmToolCall;

/**
 * Built-in Tool 단위 실행 계약.
 */
public interface BuiltInToolCommand {

    String toolName();

    String execute(LlmToolCall call, BuiltInToolContext context) throws Exception;
}
