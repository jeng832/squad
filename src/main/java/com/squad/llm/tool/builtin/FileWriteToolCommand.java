package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * workspace 내 파일 쓰기 Built-in Tool.
 *
 * <p>지정된 경로에 내용을 작성하거나 추가한다.
 * 디스크 고갈 방지를 위해 최대 쓰기 크기가 제한된다.</p>
 *
 * @see BuiltInToolCommand
 */
@Component
public class FileWriteToolCommand implements BuiltInToolCommand {

    private static final long MAX_WRITE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    @Override
    public LlmTool definition() {
        return new LlmTool(
                "file_write",
                "Write content to file in workspace (max 5MB per write)",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Path relative to workspace"),
                                "content", Map.of("type", "string", "description", "Content to write (max 5MB)"),
                                "append", Map.of("type", "boolean", "description", "Append instead of overwrite")
                        ),
                        "required", List.of("path", "content")
                )
        );
    }

    @Override
    public String execute(LlmToolCall call, BuiltInToolContext context) throws Exception {
        String rawPath = context.requiredString(call.arguments(), "path");
        String content = context.requiredString(call.arguments(), "content");
        boolean append = context.booleanValue(call.arguments(), "append", false);

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        if (contentBytes.length > MAX_WRITE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "쓰기 내용이 너무 큽니다 (최대 5MB): " + contentBytes.length + " bytes");
        }

        Path path = context.resolveWithinWorkspace(rawPath);
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
        return String.format("ok: wrote %d chars to %s (%s)", content.length(), rawPath, mode);
    }
}
