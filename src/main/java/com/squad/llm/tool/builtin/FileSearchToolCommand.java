package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
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

        List<String> matches;
        try (Stream<Path> stream = Files.walk(base)) {
            matches = stream.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> isSafeWorkspaceFile(file, context))
                    .filter(file -> matcher.matches(base.relativize(file)))
                    .filter(file -> matchesPattern(file, pattern))
                    .limit(resultLimit)
                    .map(file -> context.workspaceRoot().relativize(file).toString())
                    .toList();
        }

        if (matches.isEmpty()) {
            return "검색 결과가 없습니다.";
        }

        return String.join("\n", matches);
    }

    private boolean matchesPattern(Path file, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        try {
            return Files.readString(file).contains(pattern);
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isSafeWorkspaceFile(Path file, BuiltInToolContext context) {
        try {
            String relativePath = context.workspaceRoot().relativize(file).toString();
            context.resolveWithinWorkspace(relativePath);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
