package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class BashExecToolCommand implements BuiltInToolCommand {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 12000;

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
        int timeoutSeconds = context.intValue(call.arguments(), "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS);
        timeoutSeconds = Math.max(1, Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS));

        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-lc", command)
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
}
