package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * workspace 내 파일 읽기 Built-in Tool.
 *
 * <p>지정된 경로의 파일 내용을 읽어 반환한다.
 * OOM 방지를 위해 최대 읽기 크기가 제한된다.</p>
 *
 * @see BuiltInToolCommand
 */
@Component
public class FileReadToolCommand implements BuiltInToolCommand {

    private static final long MAX_READ_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    @Override
    public LlmTool definition() {
        return new LlmTool(
                "file_read",
                "Read file content from workspace (max 10MB)",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Path relative to workspace")
                        ),
                        "required", List.of("path")
                )
        );
    }

    @Override
    public String execute(LlmToolCall call, BuiltInToolContext context) throws Exception {
        String rawPath = context.requiredString(call.arguments(), "path");
        Path path = context.resolveWithinWorkspace(rawPath);

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("파일을 찾을 수 없습니다: " + rawPath);
        }

        long fileSize = Files.size(path);
        if (fileSize > MAX_READ_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "파일이 너무 큽니다 (최대 10MB): " + rawPath + " (" + fileSize + " bytes)");
        }

        return Files.readString(path);
    }
}
