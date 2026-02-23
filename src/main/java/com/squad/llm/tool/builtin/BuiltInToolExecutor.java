package com.squad.llm.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.squad.agent.runner.ContainerLifecycleManager;
import com.squad.agent.runner.DockerContainerManager;
import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolExecutor;
import com.squad.llm.tool.LlmToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent Runtime 내장 도구 실행기.
 */
@Slf4j
@Component
public class BuiltInToolExecutor implements LlmToolExecutor {

    private static final int CONTAINER_EXEC_TIMEOUT_SECONDS = 30;

    private final BuiltInToolRegistry toolRegistry;
    private final BuiltInToolContext context;
    private final Map<String, BuiltInToolCommand> commandMap;
    private final DockerClient dockerClient;
    private final DockerContainerManager dockerContainerManager;
    private final ContainerLifecycleManager containerLifecycleManager;
    private final ObjectMapper objectMapper;

    @Autowired
    public BuiltInToolExecutor(
            BuiltInToolRegistry toolRegistry,
            List<BuiltInToolCommand> commands,
            @Value("${squad.builtin-tools.workspace-root:/tmp/squad-workspace}") String workspaceRoot,
            DockerClient dockerClient,
            DockerContainerManager dockerContainerManager,
            ContainerLifecycleManager containerLifecycleManager,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.context = new BuiltInToolContext(Path.of(workspaceRoot).toAbsolutePath().normalize());
        this.commandMap = commands.stream()
                .collect(Collectors.toUnmodifiableMap(BuiltInToolCommand::toolName, Function.identity()));
        this.dockerClient = dockerClient;
        this.dockerContainerManager = dockerContainerManager;
        this.containerLifecycleManager = containerLifecycleManager;
        this.objectMapper = objectMapper;
    }

    public BuiltInToolExecutor(
            BuiltInToolRegistry toolRegistry,
            List<BuiltInToolCommand> commands,
            String workspaceRoot
    ) {
        this.toolRegistry = toolRegistry;
        this.context = new BuiltInToolContext(Path.of(workspaceRoot).toAbsolutePath().normalize());
        this.commandMap = commands.stream()
                .collect(Collectors.toUnmodifiableMap(BuiltInToolCommand::toolName, Function.identity()));
        this.dockerClient = null;
        this.dockerContainerManager = null;
        this.containerLifecycleManager = null;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LlmToolResult execute(LlmToolCall toolCall) {
        Objects.requireNonNull(toolCall, "toolCall은 null일 수 없습니다");

        if (!toolRegistry.isBuiltInTool(toolCall.name())) {
            return error(toolCall, "지원하지 않는 Built-in Tool입니다: " + toolCall.name());
        }

        BuiltInToolCommand command = commandMap.get(toolCall.name());
        if (command == null) {
            return error(toolCall, "Built-in Tool 구현체를 찾을 수 없습니다: " + toolCall.name());
        }

        try {
            String output = executeWithContainerFallback(toolCall, command);
            return ok(toolCall, output);
        } catch (Exception e) {
            log.warn("Built-in Tool 실행 실패: tool={}, callId={}", toolCall.name(), toolCall.id(), e);
            return error(toolCall, e.getMessage());
        }
    }

    public boolean supports(String toolName) {
        return toolRegistry.isBuiltInTool(toolName);
    }

    private String executeWithContainerFallback(LlmToolCall call, BuiltInToolCommand localCommand) throws Exception {
        ToolExecutionContextHolder.ToolExecutionContext executionContext = ToolExecutionContextHolder.get();
        if (executionContext == null || dockerClient == null || dockerContainerManager == null
                || containerLifecycleManager == null) {
            return localCommand.execute(call, context);
        }
        return executeInContainer(call, executionContext);
    }

    private String executeInContainer(LlmToolCall call, ToolExecutionContextHolder.ToolExecutionContext executionContext) throws Exception {
        String containerName = containerLifecycleManager.buildContainerName(
                String.valueOf(executionContext.sessionId()),
                String.valueOf(executionContext.agentId())
        );
        Optional<com.github.dockerjava.api.model.Container> container = dockerContainerManager.findByName(containerName);
        if (container.isEmpty()) {
            throw new IllegalStateException("실행 대상 컨테이너를 찾을 수 없습니다: " + containerName);
        }
        String containerId = container.get().getId();
        assertContainerRunning(containerName, containerId);

        String payload = encodePayload(call);
        ExecCreateCmdResponse exec = createExecCommand(containerName, containerId, payload);

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr);
        boolean completed = dockerClient.execStartCmd(exec.getId())
                .exec(callback)
                .awaitCompletion(CONTAINER_EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            throw new IllegalArgumentException("Built-in Tool 컨테이너 실행 시간 초과");
        }

        Long exitCode = dockerClient.inspectExecCmd(exec.getId()).exec().getExitCodeLong();
        String output = stdout.toString(StandardCharsets.UTF_8);
        String errorOutput = stderr.toString(StandardCharsets.UTF_8);
        if (exitCode != null && exitCode != 0L) {
            if (errorOutput.contains("ClassNotFoundException: com.squad.agent.runner.AgentToolCliApplication")
                    || errorOutput.contains("Could not find or load main class com.squad.agent.runner.AgentToolCliApplication")) {
                throw new IllegalStateException(
                        "Agent Tool CLI 진입점 로드에 실패했습니다. 이미지/실행 방식 불일치일 수 있습니다. "
                                + "새 이미지로 재빌드 후 새 세션으로 다시 시작하세요. "
                                + "(권장: ./scripts/start_local_with_versioned_image.sh)");
            }
            throw new IllegalArgumentException("컨테이너 실행 실패(exit=" + exitCode + "): " + errorOutput);
        }
        if (!errorOutput.isBlank()) {
            log.debug("Built-in Tool 컨테이너 stderr: {}", errorOutput);
        }
        return output;
    }

    private ExecCreateCmdResponse createExecCommand(String containerName, String containerId, String payload) {
        try {
            return dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(
                            "java",
                            "-Dloader.main=com.squad.agent.runner.AgentToolCliApplication",
                            "-cp", "/app/agent-runner.jar",
                            "org.springframework.boot.loader.launch.PropertiesLauncher",
                            payload
                    )
                    .exec();
        } catch (ConflictException e) {
            throw new IllegalStateException(
                    "실행 대상 컨테이너가 실행 중이 아닙니다. 세션을 다시 시작해 새 컨테이너를 생성하세요: " + containerName, e);
        }
    }

    private void assertContainerRunning(String containerName, String containerId) {
        Optional<InspectContainerResponse.ContainerState> state = dockerContainerManager.getState(containerId);
        if (state.isEmpty() || !Boolean.TRUE.equals(state.get().getRunning())) {
            throw new IllegalStateException(buildContainerNotRunningMessage(containerName, state.orElse(null)));
        }
    }

    private String buildContainerNotRunningMessage(String containerName, InspectContainerResponse.ContainerState state) {
        StringBuilder message = new StringBuilder("실행 대상 컨테이너가 실행 중이 아닙니다. 세션을 다시 시작해 새 컨테이너를 생성하세요: ")
                .append(containerName);

        if (state != null && state.getStatus() != null) {
            message.append(" (status=").append(state.getStatus());
            if (state.getExitCodeLong() != null) {
                message.append(", exitCode=").append(state.getExitCodeLong());
            }
            message.append(")");

            if ("exited".equalsIgnoreCase(state.getStatus())) {
                message.append(" 원인 후보: Git clone 인증 실패(PAT/Secret 누락), entrypoint 실행 오류.");
            }
        }
        return message.toString();
    }

    private String encodePayload(LlmToolCall call) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", call.id());
        payload.put("name", call.name());
        payload.put("arguments", call.arguments());
        String json = objectMapper.writeValueAsString(payload);
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private LlmToolResult ok(LlmToolCall call, String output) {
        return new LlmToolResult(call.id(), call.name(), output);
    }

    private LlmToolResult error(LlmToolCall call, String message) {
        return new LlmToolResult(call.id(), call.name(), "[오류] " + message);
    }
}
