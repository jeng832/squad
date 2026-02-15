package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent Runtime에 기본 제공되는 Built-in Tool 정의 레지스트리.
 */
@Service
public class BuiltInToolRegistry {

    private static final Set<String> TOOL_NAMES = Set.of(
            "file_read",
            "file_write",
            "file_search",
            "bash_exec"
    );

    private static final List<LlmTool> TOOLS = List.of(
            new LlmTool(
                    "file_read",
                    "Read file content from workspace",
                    schema(
                            Map.of(
                                    "path", Map.of("type", "string", "description", "Path relative to workspace")
                            ),
                            List.of("path")
                    )
            ),
            new LlmTool(
                    "file_write",
                    "Write content to file in workspace",
                    schema(
                            Map.of(
                                    "path", Map.of("type", "string", "description", "Path relative to workspace"),
                                    "content", Map.of("type", "string", "description", "Content to write"),
                                    "append", Map.of("type", "boolean", "description", "Append instead of overwrite")
                            ),
                            List.of("path", "content")
                    )
            ),
            new LlmTool(
                    "file_search",
                    "Search files under workspace by glob and optional text pattern",
                    schema(
                            Map.of(
                                    "path", Map.of("type", "string", "description", "Base path, default '.'"),
                                    "glob", Map.of("type", "string", "description", "Glob filter, default '**/*'"),
                                    "pattern", Map.of("type", "string", "description", "Text pattern to match"),
                                    "maxResults", Map.of("type", "integer", "description", "Max matched files, default 100")
                            ),
                            List.of()
                    )
            ),
            new LlmTool(
                    "bash_exec",
                    "Execute shell command in workspace",
                    schema(
                            Map.of(
                                    "command", Map.of("type", "string", "description", "Shell command to execute"),
                                    "timeoutSeconds", Map.of("type", "integer", "description", "Execution timeout in seconds, default 30")
                            ),
                            List.of("command")
                    )
            )
    );

    public List<LlmTool> getTools() {
        return TOOLS;
    }

    public boolean isBuiltInTool(String toolName) {
        return TOOL_NAMES.contains(toolName);
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", required
        );
    }
}
