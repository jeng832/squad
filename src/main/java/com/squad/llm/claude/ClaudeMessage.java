package com.squad.llm.claude;

import java.util.List;

record ClaudeMessage(
        String role,
        List<ClaudeContent> content
) {
}
