package com.squad.agent.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentContainerHealthCheckerTest {

    @Test
    void unhealthy_컨테이너를_재시작한다() {
        DockerClient dockerClient = Mockito.mock(DockerClient.class);
        DockerContainerManager containerManager = Mockito.mock(DockerContainerManager.class);
        ListContainersCmd listCmd = Mockito.mock(ListContainersCmd.class);
        Container container = Mockito.mock(Container.class);
        InspectContainerResponse.ContainerState state = Mockito.mock(InspectContainerResponse.ContainerState.class);
        RestartContainerCmd restartCmd = Mockito.mock(RestartContainerCmd.class);

        when(container.getId()).thenReturn("cid");
        when(container.getNames()).thenReturn(new String[]{"/squad-abc"});
        when(state.getStatus()).thenReturn("exited");

        when(dockerClient.listContainersCmd()).thenReturn(listCmd);
        when(listCmd.withShowAll(true)).thenReturn(listCmd);
        when(listCmd.exec()).thenReturn(List.of(container));

        when(containerManager.getState("cid")).thenReturn(Optional.of(state));
        when(dockerClient.restartContainerCmd("cid")).thenReturn(restartCmd);

        AgentContainerHealthChecker checker = new AgentContainerHealthChecker(
                dockerClient,
                containerManager,
                "squad-"
        );

        checker.checkContainers();

        verify(restartCmd).exec();
    }
}
