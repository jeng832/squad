package com.squad.llm.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record ClaudeRequest(
        String model,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        @JsonProperty("system") String systemPrompt,
        List<ClaudeMessage> messages,
        List<ClaudeTool> tools
) {
}
