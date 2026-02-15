package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class FileReadToolCommand implements BuiltInToolCommand {

    @Override
    public String toolName() {
        return "file_read";
    }

    @Override
    public String execute(LlmToolCall call, BuiltInToolContext context) throws Exception {
        String rawPath = context.requiredString(call.arguments(), "path");
        Path path = context.resolveWithinWorkspace(rawPath);

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("파일을 찾을 수 없습니다: " + rawPath);
        }

        return Files.readString(path);
    }
}
