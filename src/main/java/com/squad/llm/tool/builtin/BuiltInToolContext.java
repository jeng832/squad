package com.squad.llm.tool.builtin;

import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Built-in Tool 실행 시 공통으로 사용하는 workspace/인자 유틸.
 */
public class BuiltInToolContext {

    private final Path workspaceRoot;
    private final Path workspaceRootRealPath;

    public BuiltInToolContext(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.workspaceRootRealPath = resolveWorkspaceRealPath(this.workspaceRoot);
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public Path resolveWithinWorkspace(String rawPath) {
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

        Path anchor = findExistingAnchor(normalized);
        if (anchor == null) {
            throw new IllegalArgumentException("workspace 경로를 확인할 수 없습니다: " + rawPath);
        }

        Path anchorRealPath = toRealPath(anchor);
        if (!anchorRealPath.startsWith(workspaceRootRealPath)) {
            throw new IllegalArgumentException("workspace 밖 경로는 접근할 수 없습니다: " + rawPath);
        }

        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            Path normalizedRealPath = toRealPath(normalized);
            if (!normalizedRealPath.startsWith(workspaceRootRealPath)) {
                throw new IllegalArgumentException("workspace 밖 경로는 접근할 수 없습니다: " + rawPath);
            }
        }
        return normalized;
    }

    public String requiredString(Map<String, Object> args, String key) {
        String value = stringValue(args, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + "는 필수입니다.");
        }
        return value;
    }

    public String stringValue(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(args.get(key));
    }

    public boolean booleanValue(Map<String, Object> args, String key, boolean defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        Object value = args.get(key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public int intValue(Map<String, Object> args, String key, int defaultValue) {
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

    private Path resolveWorkspaceRealPath(Path rootPath) {
        if (Files.exists(rootPath, LinkOption.NOFOLLOW_LINKS)) {
            return toRealPath(rootPath);
        }
        return rootPath;
    }

    private Path findExistingAnchor(Path path) {
        Path current = path;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        return current;
    }

    private Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (Exception e) {
            throw new IllegalArgumentException("경로 확인에 실패했습니다: " + path, e);
        }
    }
}
