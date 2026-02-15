package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * workspace 내 파일 검색 Built-in Tool.
 *
 * <p>glob 패턴과 텍스트 패턴을 조합하여 파일을 검색한다.
 * symlink를 통한 workspace 탈출을 방지하며, 탐색 깊이와 결과 수가 제한된다.</p>
 *
 * @see BuiltInToolCommand
 */
@Component
public class FileSearchToolCommand implements BuiltInToolCommand {

    private static final int DEFAULT_SEARCH_LIMIT = 100;
    private static final int MAX_SEARCH_LIMIT = 500;
    private static final int MAX_WALK_DEPTH = 20;
    private static final long MAX_PATTERN_SCAN_FILE_SIZE_BYTES = 1_000_000L;

    @Override
    public LlmTool definition() {
        return new LlmTool(
                "file_search",
                "Search files under workspace by glob and optional text pattern",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Base path, default '.'"),
                                "glob", Map.of("type", "string", "description", "Glob filter, default '**'"),
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
        String glob = context.stringValue(call.arguments(), "glob", "**");
        String pattern = context.stringValue(call.arguments(), "pattern", null);
        int maxResults = context.intValue(call.arguments(), "maxResults", DEFAULT_SEARCH_LIMIT);
        final int resultLimit = Math.max(1, Math.min(maxResults, MAX_SEARCH_LIMIT));

        Path base = context.resolveWithinWorkspace(basePath);
        if (!Files.exists(base)) {
            throw new IllegalArgumentException("검색 경로를 찾을 수 없습니다: " + basePath);
        }

        PathMatcher matcher = base.getFileSystem().getPathMatcher("glob:" + glob);

        List<String> matches;
        try (Stream<Path> stream = Files.walk(base, MAX_WALK_DEPTH)) {
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
            if (Files.size(file) > MAX_PATTERN_SCAN_FILE_SIZE_BYTES) {
                return false;
            }
        } catch (IOException ignored) {
            return false;
        }

        try {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(pattern)) {
                        return true;
                    }
                }
                return false;
            }
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
