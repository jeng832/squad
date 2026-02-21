package com.squad.agent.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Agent Container의 생성/시작/중지/삭제를 관리합니다.
 */
@Service
public class ContainerLifecycleManager {

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
        verifyRunning(containerId);
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

    private void verifyRunning(String containerId) {
        InspectContainerResponse.ContainerState state = dockerClient.inspectContainerCmd(containerId).exec().getState();
        if (state == null || !Boolean.TRUE.equals(state.getRunning())) {
            Long exitCode = state != null ? state.getExitCodeLong() : null;
            stopAndRemoveContainer(containerId);
            throw new IllegalStateException("Agent 컨테이너 시작 직후 실행 상태가 아닙니다: containerId="
                    + containerId + ", exitCode=" + exitCode);
        }
    }
}
