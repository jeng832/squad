package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmToolCall;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class FileWriteToolCommand implements BuiltInToolCommand {

    @Override
    public String toolName() {
        return "file_write";
    }

    @Override
    public String execute(LlmToolCall call, BuiltInToolContext context) throws Exception {
        String rawPath = context.requiredString(call.arguments(), "path");
        String content = context.requiredString(call.arguments(), "content");
        boolean append = context.booleanValue(call.arguments(), "append", false);

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
