package com.squad.agent.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Agent Container의 생성/시작/중지/삭제를 관리합니다.
 */
@Slf4j
@Service
public class ContainerLifecycleManager {
    private static final int STARTUP_CHECK_RETRIES = 25;
    private static final long STARTUP_CHECK_DELAY_MS = 200L;

    private final DockerClient dockerClient;
    private final DockerContainerManager dockerContainerManager;
    private final String agentImage;
    private final String network;

    public ContainerLifecycleManager(
            DockerClient dockerClient,
            DockerContainerManager dockerContainerManager,
            @Value("${squad.docker.agent-image:squad-agent:latest}") String agentImage,
            @Value("${squad.docker.network:squad-network}") String network
    ) {
        this.dockerClient = dockerClient;
        this.dockerContainerManager = dockerContainerManager;
        this.agentImage = agentImage;
        this.network = network;
    }

    public String buildContainerName(String sessionId, String agentId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId가 비어 있습니다.");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId가 비어 있습니다.");
        }
        return "squad-" + sessionId + "-" + agentId;
    }

    public String createAndStartContainer(String sessionId, String agentId, List<String> env) {
        String containerId = createContainer(sessionId, agentId, env);
        startContainer(containerId);
        verifyRunning(sessionId, agentId, containerId);
        return containerId;
    }

    public String createContainer(String sessionId, String agentId, List<String> env) {
        String name = buildContainerName(sessionId, agentId);
        removeIfExists(name);

        CreateContainerCmd cmd = dockerClient.createContainerCmd(agentImage)
                .withName(name)
                .withNetworkMode(network);
        if (env != null && !env.isEmpty()) {
            cmd.withEnv(env);
        }
        CreateContainerResponse response;
        try {
            response = cmd.exec();
        } catch (NotFoundException e) {
            String message = "Agent 이미지가 없습니다: " + agentImage
                    + ". 먼저 `docker build -f docker/agent/Dockerfile -t "
                    + agentImage + " .` 를 실행하세요.";
            throw new IllegalStateException(message, e);
        }
        return response.getId();
    }

    public void startContainer(String containerId) {
        dockerClient.startContainerCmd(containerId).exec();
    }

    public void stopAndRemoveContainer(String containerId) {
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
        } catch (NotModifiedException ignored) {
            // 이미 종료된 컨테이너는 remove 단계만 수행한다.
        }
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();
    }

    private void removeIfExists(String name) {
        Optional<Container> existing = dockerContainerManager.findByName(name);
        if (existing.isEmpty()) {
            return;
        }
        RemoveContainerCmd removeCmd = dockerClient.removeContainerCmd(existing.get().getId())
                .withForce(true);
        removeCmd.exec();
    }

    private void verifyRunning(String sessionId, String agentId, String containerId) {
        InspectContainerResponse inspect = null;
        for (int i = 0; i < STARTUP_CHECK_RETRIES; i++) {
            inspect = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = inspect.getState();
            if (state != null && Boolean.TRUE.equals(state.getRunning())) {
                log.info("Agent 컨테이너 시작 확인: sessionId={}, agentId={}, containerId={}, image={}, imageId={}",
                        sessionId, agentId, containerId, agentImage, inspect.getImageId());
                return;
            }
            if (state != null && "exited".equalsIgnoreCase(state.getStatus())) {
                Long exitCode = state.getExitCodeLong();
                String recentLogs = readRecentLogs(containerId);
                stopAndRemoveContainer(containerId);
                throw new IllegalStateException("Agent 컨테이너가 시작 직후 종료되었습니다: containerId="
                        + containerId + ", exitCode=" + exitCode + ", logs=" + recentLogs);
            }
            sleep(STARTUP_CHECK_DELAY_MS);
        }
        Long exitCode = inspect != null && inspect.getState() != null ? inspect.getState().getExitCodeLong() : null;
        stopAndRemoveContainer(containerId);
        throw new IllegalStateException("Agent 컨테이너가 실행 상태로 안정화되지 않았습니다: containerId="
                + containerId + ", exitCode=" + exitCode);
    }

    private String readRecentLogs(String containerId) {
        try {
            StringBuilder out = new StringBuilder();
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(20)
                    .exec(new LogContainerResultCallback() {
                        @Override
                        public void onNext(Frame item) {
                            out.append(new String(item.getPayload(), StandardCharsets.UTF_8).replace('\n', ' ').trim());
                            out.append(" | ");
                        }
                    })
                    .awaitCompletion(2, TimeUnit.SECONDS);
            return out.toString();
        } catch (Exception e) {
            return "log-read-failed:" + e.getClass().getSimpleName();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
