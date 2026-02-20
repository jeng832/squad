package com.squad.llm.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.squad.agent.runner.ContainerLifecycleManager;
import com.squad.agent.runner.DockerContainerManager;
import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BuiltInToolExecutor 단위 테스트")
class BuiltInToolExecutorTest {

    @TempDir
    Path workspace;

    @Test
    @DisplayName("file_write와 file_read로 파일 쓰기/읽기를 수행한다")
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
    @DisplayName("file_search는 glob과 pattern으로 파일을 검색한다")
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
    @DisplayName("file_search는 대용량 파일의 pattern 스캔을 건너뛴다")
    void fileSearchSkipsOversizedFileForPatternScan() throws Exception {
        BuiltInToolExecutor executor = createExecutor();

        Files.createDirectories(workspace.resolve("src"));
        String content = "a".repeat(1_100_000) + "keyword";
        Files.writeString(workspace.resolve("src/large.txt"), content);

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c3-2",
                "file_search",
                Map.of("path", "src", "glob", "*.txt", "pattern", "keyword")
        ));

        assertThat(result.output()).contains("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("workspace 밖 경로 접근을 차단한다")
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
    @DisplayName("symlink를 통한 workspace 탈출을 차단한다")
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
    @DisplayName("file_search에서 workspace 밖 symlink 파일을 무시한다")
    @DisabledOnOs(OS.WINDOWS)
    void fileSearchIgnoresSymlinkOutsideWorkspace() throws Exception {
        BuiltInToolExecutor executor = createExecutor();
        Path outside = Files.createTempFile("outside-search-", ".txt");
        Files.writeString(outside, "secret keyword");
        Files.createDirectories(workspace.resolve("src"));
        Files.createSymbolicLink(workspace.resolve("src/link.txt"), outside);

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c4-3",
                "file_search",
                Map.of("path", "src", "glob", "*.txt", "pattern", "keyword")
        ));

        assertThat(result.output()).doesNotContain("src/link.txt");
        assertThat(result.output()).contains("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("bash_exec는 workspace 내에서 명령을 실행한다")
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
    @DisplayName("bash_exec는 절대 경로를 통한 workspace 밖 접근을 차단한다")
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
    @DisplayName("bash_exec는 long option 값의 workspace 밖 경로를 차단한다")
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
    @DisplayName("bash_exec는 shell 메타 문자를 차단한다")
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
    @DisplayName("bash_exec는 allowlist에 없는 명령을 차단한다")
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
    @DisplayName("bash_exec는 find 명령을 허용한다")
    void bashExecAllowsFindCommand() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c9",
                "bash_exec",
                Map.of("command", "find . -name \"*.txt\"")
        ));

        assertThat(result.output()).contains("exitCode=");
        assertThat(result.output()).doesNotContain("[오류]");
    }

    @Test
    @DisplayName("bash_exec는 sed 명령을 허용한다")
    void bashExecAllowsSedCommand() throws Exception {
        BuiltInToolExecutor executor = createExecutor();
        Files.writeString(workspace.resolve("notes.txt"), "line1\nline2\n");

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c10",
                "bash_exec",
                Map.of("command", "sed -n 1p notes.txt")
        ));

        assertThat(result.output()).contains("exitCode=0");
        assertThat(result.output()).doesNotContain("[오류]");
    }

    @Test
    @DisplayName("bash_exec는 git 명령을 허용한다")
    void bashExecAllowsGitCommand() {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c9-2",
                "bash_exec",
                Map.of("command", "git --version")
        ));

        assertThat(result.output()).contains("exitCode=");
        assertThat(result.output()).doesNotContain("[오류]");
    }

    @Test
    @DisplayName("bash_exec는 sort 명령을 허용한다")
    void bashExecAllowsSortCommand() throws Exception {
        BuiltInToolExecutor executor = createExecutor();
        Files.writeString(workspace.resolve("data.txt"), "b\na\nc\n");

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c9-3",
                "bash_exec",
                Map.of("command", "sort data.txt")
        ));

        assertThat(result.output()).contains("exitCode=0");
        assertThat(result.output()).contains("a");
    }

    @Test
    @DisplayName("bash_exec는 diff 명령을 허용한다")
    void bashExecAllowsDiffCommand() throws Exception {
        BuiltInToolExecutor executor = createExecutor();
        Files.writeString(workspace.resolve("a.txt"), "hello\n");
        Files.writeString(workspace.resolve("b.txt"), "world\n");

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c9-4",
                "bash_exec",
                Map.of("command", "diff a.txt b.txt")
        ));

        assertThat(result.output()).contains("exitCode=");
        assertThat(result.output()).doesNotContain("[오류]");
    }

    @Test
    @DisplayName("bash_exec는 비정상 종료 코드를 exitCode와 함께 반환한다")
    void bashExecReturnsNonZeroExitCode() throws Exception {
        BuiltInToolExecutor executor = createExecutor();

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c11",
                "bash_exec",
                Map.of("command", "ls nonexistent-file-xyz")
        ));

        assertThat(result.output()).contains("exitCode=");
        assertThat(result.output()).doesNotContain("[오류]");
    }

    @Test
    @DisplayName("bash_exec는 cat으로 workspace 밖 symlink 파일 읽기를 차단한다")
    @DisabledOnOs(OS.WINDOWS)
    void bashExecBlocksCatOnSymlinkOutsideWorkspace() throws Exception {
        BuiltInToolExecutor executor = createExecutor();
        Path outside = Files.createTempFile("bash-outside-", ".txt");
        Files.writeString(outside, "secret-content");
        Files.createSymbolicLink(workspace.resolve("link-secret"), outside);

        LlmToolResult result = executor.execute(new LlmToolCall(
                "c12",
                "bash_exec",
                Map.of("command", "cat link-secret")
        ));

        assertThat(result.output()).contains("[오류]");
        assertThat(result.output()).contains("workspace 밖");
    }

    @Test
    @DisplayName("컨테이너가 실행 중이 아니면 Built-in Tool 실행을 중단한다")
    void containerToolExecutionFailsWhenContainerNotRunning() {
        DockerClient dockerClient = Mockito.mock(DockerClient.class);
        DockerContainerManager containerManager = Mockito.mock(DockerContainerManager.class);
        ContainerLifecycleManager lifecycleManager = Mockito.mock(ContainerLifecycleManager.class);
        Container container = Mockito.mock(Container.class);
        InspectContainerResponse.ContainerState state = Mockito.mock(InspectContainerResponse.ContainerState.class);

        when(lifecycleManager.buildContainerName("1", "2")).thenReturn("squad-1-2");
        when(containerManager.findByName("squad-1-2")).thenReturn(Optional.of(container));
        when(container.getId()).thenReturn("cid-1");
        when(containerManager.getState("cid-1")).thenReturn(Optional.of(state));
        when(state.getRunning()).thenReturn(false);

        var commands = List.of(
                new FileReadToolCommand(),
                new FileWriteToolCommand(),
                new FileSearchToolCommand(),
                new BashExecToolCommand()
        );
        BuiltInToolExecutor executor = new BuiltInToolExecutor(
                new BuiltInToolRegistry(commands),
                commands,
                workspace.toString(),
                dockerClient,
                containerManager,
                lifecycleManager,
                new ObjectMapper()
        );

        ToolExecutionContextHolder.set(1L, 2L);
        try {
            LlmToolResult result = executor.execute(new LlmToolCall(
                    "c13",
                    "file_search",
                    Map.of("path", ".", "glob", "**")
            ));

            assertThat(result.output()).contains("[오류]");
            assertThat(result.output()).contains("실행 중이 아닙니다");
            verify(dockerClient, never()).execCreateCmd(anyString());
        } finally {
            ToolExecutionContextHolder.clear();
        }
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
