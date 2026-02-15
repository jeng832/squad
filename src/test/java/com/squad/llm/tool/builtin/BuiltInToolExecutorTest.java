package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInToolExecutorTest {

    @TempDir
    Path workspace;

    @Test
    void fileWriteAndRead() {
        BuiltInToolExecutor executor = new BuiltInToolExecutor(new BuiltInToolRegistry(), workspace.toString());

        LlmToolResult write = executor.execute(new LlmToolCall(
                "c1",
                "file_write",
                Map.of("path", "notes/todo.txt", "content", "hello")
        ));
        LlmToolResult read = executor.execute(new LlmToolCall(
                "c2",
                "file_read",
                Map.of("path", "notes/todo.txt")
        ));

        assertThat(write.output()).contains("ok: wrote");
        assertThat(read.output()).isEqualTo("hello");
    }

    @Test
    void fileSearchByGlobAndPattern() throws Exception {
        BuiltInToolExecutor executor = new BuiltInToolExecutor(new BuiltInToolRegistry(), workspace.toString());

        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/a.txt"), "alpha keyword");
        Files.writeString(workspace.resolve("src/b.md"), "beta keyword");

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c3",
                "file_search",
                Map.of("path", "src", "glob", "*.txt", "pattern", "keyword")
        ));

        assertThat(result.output()).contains("src/a.txt");
        assertThat(result.output()).doesNotContain("src/b.md");
    }

    @Test
    void blocksPathTraversalOutsideWorkspace() {
        BuiltInToolExecutor executor = new BuiltInToolExecutor(new BuiltInToolRegistry(), workspace.toString());

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c4",
                "file_read",
                Map.of("path", "../secret.txt")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("workspace 밖");
    }

    @Test
    void bashExecRunsInWorkspace() {
        BuiltInToolExecutor executor = new BuiltInToolExecutor(new BuiltInToolRegistry(), workspace.toString());

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c5",
                "bash_exec",
                Map.of("command", "pwd")
        ));

        assertThat(result.output()).contains("exitCode=0");
        assertThat(result.output()).contains(workspace.toAbsolutePath().normalize().toString());
    }
}
