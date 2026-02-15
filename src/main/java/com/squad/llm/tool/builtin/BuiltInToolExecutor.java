package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Agent Runtime 내장 도구 실행기.
 */
@Slf4j
@Component
public class BuiltInToolExecutor implements LlmToolExecutor {

    private static final int DEFAULT_SEARCH_LIMIT = 100;
    private static final int MAX_SEARCH_LIMIT = 500;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 12000;

    private final BuiltInToolRegistry toolRegistry;
    private final Path workspaceRoot;

    public BuiltInToolExecutor(
            BuiltInToolRegistry toolRegistry,
            @Value("${squad.builtin-tools.workspace-root:/workspace}") String workspaceRoot
    ) {
        this.toolRegistry = toolRegistry;
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public LlmToolResult execute(LlmToolCall toolCall) {
        Objects.requireNonNull(toolCall, "toolCall은 null일 수 없습니다");

        if (!toolRegistry.isBuiltInTool(toolCall.name())) {
            return error(toolCall, "지원하지 않는 Built-in Tool입니다: " + toolCall.name());
        }

        try {
            return switch (toolCall.name()) {
                case "file_read" -> fileRead(toolCall);
                case "file_write" -> fileWrite(toolCall);
                case "file_search" -> fileSearch(toolCall);
                case "bash_exec" -> bashExec(toolCall);
                default -> error(toolCall, "지원하지 않는 Built-in Tool입니다: " + toolCall.name());
            };
        } catch (Exception e) {
            log.warn("Built-in Tool 실행 실패: tool={}, callId={}", toolCall.name(), toolCall.id(), e);
            return error(toolCall, e.getMessage());
        }
    }

    public boolean supports(String toolName) {
        return toolRegistry.isBuiltInTool(toolName);
    }

    private LlmToolResult fileRead(LlmToolCall call) throws IOException {
        String rawPath = requiredString(call.arguments(), "path");
        Path path = resolveWithinWorkspace(rawPath);

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return error(call, "파일을 찾을 수 없습니다: " + rawPath);
        }

        String content = Files.readString(path);
        return ok(call, content);
    }

    private LlmToolResult fileWrite(LlmToolCall call) throws IOException {
        String rawPath = requiredString(call.arguments(), "path");
        String content = requiredString(call.arguments(), "content");
        boolean append = booleanValue(call.arguments(), "append", false);

        Path path = resolveWithinWorkspace(rawPath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (append) {
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        String mode = append ? "append" : "overwrite";
        return ok(call, String.format("ok: wrote %d chars to %s (%s)", content.length(), rawPath, mode));
    }

    private LlmToolResult fileSearch(LlmToolCall call) throws IOException {
        String basePath = stringValue(call.arguments(), "path", ".");
        String glob = stringValue(call.arguments(), "glob", "**/*");
        String pattern = stringValue(call.arguments(), "pattern", null);
        int maxResults = intValue(call.arguments(), "maxResults", DEFAULT_SEARCH_LIMIT);
        final int resultLimit = Math.max(1, Math.min(maxResults, MAX_SEARCH_LIMIT));

        Path base = resolveWithinWorkspace(basePath);
        if (!Files.exists(base)) {
            return error(call, "검색 경로를 찾을 수 없습니다: " + basePath);
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
                        matches.add(workspaceRoot.relativize(file).toString());
                    });
        }

        if (matches.isEmpty()) {
            return ok(call, "검색 결과가 없습니다.");
        }

        return ok(call, String.join("\n", matches));
    }

    private LlmToolResult bashExec(LlmToolCall call) throws IOException, InterruptedException {
        String command = requiredString(call.arguments(), "command");
        int timeoutSeconds = intValue(call.arguments(), "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS);
        timeoutSeconds = Math.max(1, Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS));

        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-lc", command)
                .directory(workspaceRoot.toFile())
                .redirectErrorStream(true);

        Process process = processBuilder.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return error(call, "명령 실행 시간이 초과되었습니다: " + timeoutSeconds + "s");
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (output.length() > MAX_OUTPUT_CHARS) {
            output = output.substring(0, MAX_OUTPUT_CHARS) + "\n... (truncated)";
        }

        int exitCode = process.exitValue();
        String result = "exitCode=" + exitCode + "\n" + output;
        if (exitCode != 0) {
            return error(call, result);
        }
        return ok(call, result);
    }

    private Path resolveWithinWorkspace(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("path는 비어 있을 수 없습니다.");
        }

        Path candidate;
        try {
            Path path = Path.of(rawPath);
            candidate = path.isAbsolute() ? path : workspaceRoot.resolve(path);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("유효하지 않은 path입니다: " + rawPath);
        }

        Path normalized = candidate.normalize().toAbsolutePath();
        if (!normalized.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("workspace 밖 경로는 접근할 수 없습니다: " + rawPath);
        }
        return normalized;
    }

    private String requiredString(Map<String, Object> args, String key) {
        String value = stringValue(args, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + "는 필수입니다.");
        }
        return value;
    }

    private String stringValue(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(args.get(key));
    }

    private boolean booleanValue(Map<String, Object> args, String key, boolean defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        Object value = args.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Map<String, Object> args, String key, int defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private LlmToolResult ok(LlmToolCall call, String output) {
        return new LlmToolResult(call.id(), call.name(), output);
    }

    private LlmToolResult error(LlmToolCall call, String message) {
        return new LlmToolResult(call.id(), call.name(), "[오류] " + message);
    }
}
