package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BashExecToolCommand implements BuiltInToolCommand {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 12000;
    private static final Set<Character> FORBIDDEN_META_CHARS = Set.of('|', '&', ';', '`', '$', '<', '>');
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    @Override
    public LlmTool definition() {
        return new LlmTool(
                "bash_exec",
                "Execute shell command in workspace",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of("type", "string", "description", "Shell command to execute"),
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
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalArgumentException("명령 실행 시간이 초과되었습니다: " + timeoutSeconds + "s");
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (output.length() > MAX_OUTPUT_CHARS) {
            output = output.substring(0, MAX_OUTPUT_CHARS) + "\n... (truncated)";
        }

        int exitCode = process.exitValue();
        String result = "exitCode=" + exitCode + "\n" + output;
        if (exitCode != 0) {
            throw new IllegalArgumentException(result);
        }

        return result;
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

        for (String token : tokens) {
            validateTokenPath(token, context);
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

    private void validateTokenPath(String token, BuiltInToolContext context) {
        if (token.isBlank() || token.startsWith("-")) {
            return;
        }

        if (token.contains("/") || token.equals("..") || token.startsWith("./") || token.startsWith("../")) {
            context.resolveWithinWorkspace(token);
        }
    }
}
