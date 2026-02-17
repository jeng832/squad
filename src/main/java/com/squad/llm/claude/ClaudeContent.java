package com.squad.llm.claude;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record ClaudeContent(
        String type,
        String text,
        @JsonProperty("id") String toolUseId,
        @JsonProperty("name") String toolUseName,
        @JsonProperty("input") Map<String, Object> toolUseInput
) {
}
