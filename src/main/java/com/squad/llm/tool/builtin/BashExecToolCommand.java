package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * allowlist 기반 제한 명령 실행 Built-in Tool.
 *
 * <p>허용된 명령만 workspace 내에서 실행하며, shell operator와
 * workspace 밖 경로 접근을 차단한다. symlink를 통한 workspace 탈출도 방지한다.</p>
 *
 * @see BuiltInToolCommand
 * @see BuiltInToolContext
 */
@Component
public class BashExecToolCommand implements BuiltInToolCommand {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 12000;
    private static final int MAX_OUTPUT_DRAIN_BYTES = MAX_OUTPUT_CHARS * 4;
    private static final Set<Character> FORBIDDEN_META_CHARS = Set.of('|', '&', ';', '`', '$', '<', '>');
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            // 기존
            "pwd", "ls", "cat", "echo", "grep", "wc", "head", "tail",
            "mkdir", "touch", "cp", "mv",
            // VCS
            "git",
            // 파일 검색/분석
            "find", "tree", "file", "stat", "diff",
            // 텍스트 처리
            "sort", "uniq", "cut", "tr", "sed", "awk",
            // 조합
            "xargs"
    );
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    @Override
    public LlmTool definition() {
        String allowlistedCommands = allowedCommandsDescription();
        return new LlmTool(
                "bash_exec",
                "Execute allowlisted command in workspace (no shell operators). Allowed commands: " + allowlistedCommands,
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of(
                                        "type", "string",
                                        "description", "Allowlisted command without shell operators. Allowed commands: "
                                                + allowlistedCommands
                                ),
                                "timeoutSeconds", Map.of("type", "integer", "description", "Execution timeout in seconds, default 30")
                        ),
                        "required", List.of("command")
                )
        );
    }

    @Override
    public String execute(LlmToolCall call, BuiltInToolContext context) throws Exception {
        String command = context.requiredString(call.arguments(), "command");
        List<String> commandTokens = parseAndValidateCommand(command, context);
        int timeoutSeconds = context.intValue(call.arguments(), "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS);
        timeoutSeconds = Math.max(1, Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS));

        ProcessBuilder processBuilder = new ProcessBuilder(commandTokens)
                .directory(context.workspaceRoot().toFile())
                .redirectErrorStream(true);

        Process process = processBuilder.start();
        String output = drainOutputWithTimeout(process, timeoutSeconds);

        if (output.length() > MAX_OUTPUT_CHARS) {
            output = output.substring(0, MAX_OUTPUT_CHARS) + "\n... (truncated)";
        }

        int exitCode = process.exitValue();
        return "exitCode=" + exitCode + "\n" + output;
    }

    /**
     * 프로세스의 stdout을 별도 스레드에서 drain하여 pipe deadlock을 방지한다.
     *
     * <p>타임아웃 발생 시 이미 수집된 부분 출력을 포함하여 예외를 던진다.</p>
     */
    private String drainOutputWithTimeout(Process process, int timeoutSeconds) throws Exception {
        ExecutorService drainer = Executors.newSingleThreadExecutor();
        try {
            Future<byte[]> outputFuture = drainer.submit(() -> {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                InputStream in = process.getInputStream();
                byte[] chunk = new byte[8192];
                int totalRead = 0;
                int read;
                while ((read = in.read(chunk)) != -1) {
                    int remaining = MAX_OUTPUT_DRAIN_BYTES - totalRead;
                    if (remaining <= 0) {
                        discardRemainingInput(in);
                        break;
                    }
                    int writeLen = Math.min(read, remaining);
                    buffer.write(chunk, 0, writeLen);
                    totalRead += read;
                }
                return buffer.toByteArray();
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String partial = "";
                try {
                    byte[] collected = outputFuture.get(1, TimeUnit.SECONDS);
                    partial = new String(collected, StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    // 부분 출력 수집 실패 시 빈 문자열
                }
                throw new IllegalArgumentException(
                        "명령 실행 시간이 초과되었습니다: " + timeoutSeconds + "s\n" + partial);
            }

            byte[] output = outputFuture.get(5, TimeUnit.SECONDS);
            return new String(output, StandardCharsets.UTF_8);
        } finally {
            drainer.shutdownNow();
        }
    }

    /**
     * 남은 입력 스트림을 소비하여 프로세스가 정상 종료할 수 있도록 한다.
     */
    private void discardRemainingInput(InputStream in) throws java.io.IOException {
        byte[] discard = new byte[8192];
        while (in.read(discard) != -1) {
            // 나머지 출력 버림
        }
    }

    private List<String> parseAndValidateCommand(String command, BuiltInToolContext context) {
        if (command.contains("\n") || command.contains("\r")) {
            throw new IllegalArgumentException("명령에 줄바꿈 문자를 포함할 수 없습니다.");
        }

        for (char c : command.toCharArray()) {
            if (FORBIDDEN_META_CHARS.contains(c)) {
                throw new IllegalArgumentException("명령에 허용되지 않는 메타 문자가 포함되어 있습니다.");
            }
        }

        List<String> tokens = tokenize(command);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("실행할 명령이 비어 있습니다.");
        }
        validateAllowedCommand(tokens.getFirst());

        for (int i = 1; i < tokens.size(); i++) {
            validateTokenPath(tokens.get(i), context);
        }
        return tokens;
    }

    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(command);
        int cursor = 0;

        while (matcher.find()) {
            if (!command.substring(cursor, matcher.start()).isBlank()) {
                throw new IllegalArgumentException("명령 파싱에 실패했습니다.");
            }

            String token = matcher.group(1);
            if (token == null) {
                token = matcher.group(2);
            }
            if (token == null) {
                token = matcher.group(3);
            }
            tokens.add(token);
            cursor = matcher.end();
        }

        if (!command.substring(cursor).isBlank()) {
            throw new IllegalArgumentException("명령 파싱에 실패했습니다.");
        }

        return tokens;
    }

    /**
     * 토큰이 workspace 밖 경로를 참조하는지 검증한다.
     *
     * <p>경로처럼 보이는 토큰은 {@link BuiltInToolContext#resolveWithinWorkspace(String)}로 검증한다.
     * 슬래시가 없는 단순 파일명도 workspace 내에 실제 존재하면 symlink 여부를 검증하여
     * symlink를 통한 workspace 탈출을 방지한다.</p>
     */
    private void validateTokenPath(String token, BuiltInToolContext context) {
        if (token.isBlank()) {
            return;
        }

        if (token.startsWith("-")) {
            validateOptionPath(token, context);
            return;
        }

        if (looksLikePath(token)) {
            context.resolveWithinWorkspace(token);
            return;
        }

        validatePlainFilenameSymlink(token, context);
    }

    /**
     * 옵션 토큰에서 경로 값을 추출하여 검증한다.
     */
    private void validateOptionPath(String token, BuiltInToolContext context) {
        if (token.startsWith("--") && token.contains("=")) {
            String value = token.substring(token.indexOf('=') + 1);
            if (!value.isBlank()) {
                validatePathOrFilename(value, context);
            }
        } else if (token.length() > 2) {
            String attachedValue = token.substring(2);
            if (!attachedValue.isBlank()) {
                validatePathOrFilename(attachedValue, context);
            }
        }
    }

    /**
     * 경로 또는 파일명을 검증한다.
     */
    private void validatePathOrFilename(String value, BuiltInToolContext context) {
        if (looksLikePath(value)) {
            context.resolveWithinWorkspace(value);
        } else {
            validatePlainFilenameSymlink(value, context);
        }
    }

    /**
     * 슬래시가 없는 단순 파일명이 workspace 내 symlink인 경우 workspace 탈출을 차단한다.
     *
     * <p>파일이 workspace 내에 존재하고 symlink라면 resolveWithinWorkspace로 검증하여
     * symlink 대상이 workspace 밖이면 차단한다.</p>
     */
    private void validatePlainFilenameSymlink(String filename, BuiltInToolContext context) {
        Path candidate = context.workspaceRoot().resolve(filename);
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)
                && Files.isSymbolicLink(candidate)) {
            context.resolveWithinWorkspace(filename);
        }
    }

    private boolean looksLikePath(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return token.contains("/")
                || token.equals(".")
                || token.equals("..")
                || token.startsWith("./")
                || token.startsWith("../");
    }

    private void validateAllowedCommand(String commandName) {
        if (!ALLOWED_COMMANDS.contains(commandName)) {
            throw new IllegalArgumentException("허용되지 않는 명령입니다: " + commandName);
        }
    }

    private String allowedCommandsDescription() {
        List<String> sorted = ALLOWED_COMMANDS.stream()
                .sorted()
                .toList();
        return String.join(", ", sorted);
    }
}
