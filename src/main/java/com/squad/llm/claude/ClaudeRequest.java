package com.squad.llm.claude;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record ClaudeRequest(
        String model,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        @JsonProperty("system") String systemPrompt,
        List<ClaudeMessage> messages,
        List<ClaudeTool> tools
) {
}
