package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent Runtime에 기본 제공되는 Built-in Tool 정의 레지스트리.
 */
@Service
public class BuiltInToolRegistry {

    private static final Set<String> REQUIRED_TOOL_NAMES = Set.of(
            "file_read",
            "file_write",
            "file_search",
            "bash_exec"
    );

    private final List<LlmTool> tools;
    private final Set<String> toolNames;

    public BuiltInToolRegistry(List<BuiltInToolCommand> commands) {
        this.tools = commands.stream()
                .map(BuiltInToolCommand::definition)
                .sorted(java.util.Comparator.comparing(LlmTool::name))
                .toList();

        this.toolNames = tools.stream()
                .map(LlmTool::name)
                .collect(Collectors.toUnmodifiableSet());

        if (toolNames.size() != tools.size()) {
            throw new IllegalStateException("Built-in Tool name이 중복되었습니다.");
        }

        Set<String> missingToolNames = new HashSet<>(REQUIRED_TOOL_NAMES);
        missingToolNames.removeAll(toolNames);
        if (!missingToolNames.isEmpty()) {
            throw new IllegalStateException("필수 Built-in Tool이 누락되었습니다: " + missingToolNames);
        }
    }

    public List<LlmTool> getTools() {
        return tools;
    }

    public boolean isBuiltInTool(String toolName) {
        return toolNames.contains(toolName);
    }
}
