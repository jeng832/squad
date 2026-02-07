package com.squad.llm.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

record ClaudeTool(
        String name,
        String description,
        @JsonProperty("input_schema") Map<String, Object> inputSchema
) {
}
