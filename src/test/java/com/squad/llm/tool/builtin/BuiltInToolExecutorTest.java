package com.squad.llm.tool.builtin;

import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInToolExecutorTest {

    @TempDir
    Path workspace;

    @Test
    void fileWriteAndRead() {
        BuiltInToolExecutor executor = createExecutor();

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
        BuiltInToolExecutor executor = createExecutor();

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
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c4",
                "file_read",
                Map.of("path", "../secret.txt")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("workspace 밖");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void blocksSymlinkEscapeOutsideWorkspace() throws Exception {
        BuiltInToolExecutor executor = createExecutor();
        Path outside = Files.createTempFile("outside-", ".txt");
        Files.writeString(outside, "outside");
        Files.createSymbolicLink(workspace.resolve("link-outside.txt"), outside);

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c4-2",
                "file_read",
                Map.of("path", "link-outside.txt")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("workspace 밖");
    }

    @Test
    void bashExecRunsInWorkspace() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c5",
                "bash_exec",
                Map.of("command", "pwd")
        ));

        assertThat(result.output()).contains("exitCode=0");
        assertThat(result.output()).contains(workspace.toAbsolutePath().normalize().toString());
    }

    @Test
    void bashExecBlocksAbsolutePathAccess() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c6",
                "bash_exec",
                Map.of("command", "cat /etc/passwd")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("workspace 밖");
    }

    @Test
    void bashExecBlocksPathInLongOptionValue() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c6-2",
                "bash_exec",
                Map.of("command", "cp --target-directory=/tmp /etc/hosts")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("workspace 밖");
    }

    @Test
    void bashExecBlocksShellMetaCharacters() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c7",
                "bash_exec",
                Map.of("command", "pwd && ls")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("메타 문자");
    }

    @Test
    void bashExecBlocksNonAllowlistedCommand() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c8",
                "bash_exec",
                Map.of("command", "python -c \"print('hi')\"")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("허용되지 않는 명령");
    }

    @Test
    void bashExecBlocksFindCommand() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c9",
                "bash_exec",
                Map.of("command", "find . -name \"*.txt\"")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("허용되지 않는 명령");
    }

    @Test
    void bashExecBlocksSedCommand() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c10",
                "bash_exec",
                Map.of("command", "sed -n 1p notes.txt")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("허용되지 않는 명령");
    }

    private BuiltInToolExecutor createExecutor() {
        var commands = List.of(
                new FileReadToolCommand(),
                new FileWriteToolCommand(),
                new FileSearchToolCommand(),
                new BashExecToolCommand()
        );
        return new BuiltInToolExecutor(
                new BuiltInToolRegistry(commands),
                commands,
                workspace.toString()
        );
    }
}
