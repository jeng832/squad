package com.squad.llm.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record ClaudeResponse(
        String id,
        List<ClaudeContent> content,
        @JsonProperty("stop_reason") String stopReason,
        ClaudeUsage usage
) {
}
