package com.squad.agent.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class DockerContainerManagerTest {

    @Test
    void 이름으로_컨테이너를_찾는다() {
        DockerClient dockerClient = Mockito.mock(DockerClient.class);
        Container container = Mockito.mock(Container.class);
        when(container.getId()).thenReturn("abc");
        when(container.getNames()).thenReturn(new String[]{"/squad-agent"});

        ListContainersCmd listCmd = Mockito.mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listCmd);
        when(listCmd.withShowAll(true)).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(List.of(container));

        DockerContainerManager manager = new DockerContainerManager(dockerClient);

        Optional<Container> found = manager.findByName("squad-agent");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("abc");
    }

    @Test
    void 상태를_조회한다() {
        DockerClient dockerClient = Mockito.mock(DockerClient.class);
        InspectContainerResponse inspectResponse = Mockito.mock(InspectContainerResponse.class);
        InspectContainerResponse.ContainerState state = Mockito.mock(InspectContainerResponse.ContainerState.class);
        when(state.getStatus()).thenReturn("running");

        InspectContainerCmd inspectCmd = Mockito.mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("abc")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(inspectResponse.getState()).thenReturn(state);

        DockerContainerManager manager = new DockerContainerManager(dockerClient);

        Optional<InspectContainerResponse.ContainerState> result = manager.getState("abc");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo("running");
    }
}
