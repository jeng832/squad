package com.squad.agent.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent Container 상태를 주기적으로 점검하는 Health Checker.
 */
@Slf4j
@Service
public class AgentContainerHealthChecker {

    private final DockerClient dockerClient;
    private final DockerContainerManager containerManager;
    private final String containerNamePrefix;

    public AgentContainerHealthChecker(
            DockerClient dockerClient,
            DockerContainerManager containerManager,
            @Value("${squad.docker.container-prefix:squad-}") String containerNamePrefix
    ) {
        this.dockerClient = dockerClient;
        this.containerManager = containerManager;
        this.containerNamePrefix = containerNamePrefix;
    }

    @Scheduled(fixedDelayString = "${squad.docker.health-check.interval-ms:10000}")
    public void checkContainers() {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();

        for (Container container : containers) {
            if (!matchesPrefix(container)) {
                continue;
            }
            containerManager.getState(container.getId())
                    .ifPresent(state -> handleState(container.getId(), state));
        }
    }

    private boolean matchesPrefix(Container container) {
        if (container.getNames() == null) {
            return false;
        }
        for (String name : container.getNames()) {
            String normalized = name.startsWith("/") ? name.substring(1) : name;
            if (normalized.startsWith(containerNamePrefix)) {
                return true;
            }
        }
        return false;
    }

    private void handleState(String containerId, InspectContainerResponse.ContainerState state) {
        String status = state.getStatus();
        if (status == null) {
            return;
        }
        if (status.equalsIgnoreCase("dead")) {
            restart(containerId);
            return;
        }
        if (status.equalsIgnoreCase("exited")) {
            log.warn("Agent 컨테이너가 exited 상태입니다. 자동 재시작하지 않습니다: containerId={}, exitCode={}",
                    containerId, state.getExitCodeLong());
        }
    }

    private void restart(String containerId) {
        dockerClient.restartContainerCmd(containerId).exec();
    }
}
