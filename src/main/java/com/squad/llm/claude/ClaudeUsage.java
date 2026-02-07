package com.squad.llm.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

record ClaudeUsage(
        @JsonProperty("input_tokens") Integer inputTokens,
        @JsonProperty("output_tokens") Integer outputTokens
) {
}
