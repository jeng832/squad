package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class FileSearchToolCommand implements BuiltInToolCommand {

    private static final int DEFAULT_SEARCH_LIMIT = 100;
    private static final int MAX_SEARCH_LIMIT = 500;

    @Override
    public LlmTool definition() {
        return new LlmTool(
                "file_search",
                "Search files under workspace by glob and optional text pattern",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Base path, default '.'"),
                                "glob", Map.of("type", "string", "description", "Glob filter, default '**/*'"),
                                "pattern", Map.of("type", "string", "description", "Text pattern to match"),
                                "maxResults", Map.of("type", "integer", "description", "Max matched files, default 100")
                        ),
                        "required", List.of()
                )
        );
    }

    @Override
    public String execute(LlmToolCall call, BuiltInToolContext context) throws Exception {
        String basePath = context.stringValue(call.arguments(), "path", ".");
        String glob = context.stringValue(call.arguments(), "glob", "**/*");
        String pattern = context.stringValue(call.arguments(), "pattern", null);
        int maxResults = context.intValue(call.arguments(), "maxResults", DEFAULT_SEARCH_LIMIT);
        final int resultLimit = Math.max(1, Math.min(maxResults, MAX_SEARCH_LIMIT));

        Path base = context.resolveWithinWorkspace(basePath);
        if (!Files.exists(base)) {
            throw new IllegalArgumentException("검색 경로를 찾을 수 없습니다: " + basePath);
        }

        PathMatcher matcher = base.getFileSystem().getPathMatcher("glob:" + glob);
        List<String> matches = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(base)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        if (matches.size() >= resultLimit) {
                            return;
                        }

                        Path relativeToBase = base.relativize(file);
                        if (!matcher.matches(relativeToBase)) {
                            return;
                        }

                        if (pattern != null && !pattern.isBlank()) {
                            try {
                                String content = Files.readString(file);
                                if (!content.contains(pattern)) {
                                    return;
                                }
                            } catch (IOException ignored) {
                                return;
                            }
                        }

                        matches.add(context.workspaceRoot().relativize(file).toString());
                    });
        }

        if (matches.isEmpty()) {
            return "검색 결과가 없습니다.";
        }

        return String.join("\n", matches);
    }
}
