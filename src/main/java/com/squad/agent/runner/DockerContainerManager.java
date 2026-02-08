package com.squad.agent.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.command.InspectContainerResponse;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Docker 컨테이너 조회를 위한 간단한 래퍼.
 */
@Service
public class DockerContainerManager {

    private final DockerClient dockerClient;

    public DockerContainerManager(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public Optional<Container> findByName(String name) {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();

        for (Container container : containers) {
            if (container.getNames() == null) {
                continue;
            }
            boolean matched = Arrays.stream(container.getNames())
                    .anyMatch(containerName -> containerName.equals(name) || containerName.equals("/" + name));
            if (matched) {
                return Optional.of(container);
            }
        }
        return Optional.empty();
    }

    public Optional<InspectContainerResponse.ContainerState> getState(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(dockerClient.inspectContainerCmd(id).exec().getState());
    }
}
