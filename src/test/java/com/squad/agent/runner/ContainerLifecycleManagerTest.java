package com.squad.agent.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContainerLifecycleManagerTest {

    @Test
    void 컨테이너_이름을_생성한다() {
        ContainerLifecycleManager manager = new ContainerLifecycleManager(
                Mockito.mock(DockerClient.class),
                Mockito.mock(DockerContainerManager.class),
                "agent-image",
                "squad-network"
        );

        assertThat(manager.buildContainerName("sess1", "agent1"))
                .isEqualTo("squad-sess1-agent1");
    }

    @Test
    void 컨테이너_생성_후_시작한다() {
        DockerClient dockerClient = Mockito.mock(DockerClient.class);
        DockerContainerManager containerManager = Mockito.mock(DockerContainerManager.class);
        CreateContainerCmd createCmd = Mockito.mock(CreateContainerCmd.class);
        CreateContainerResponse response = Mockito.mock(CreateContainerResponse.class);
        StartContainerCmd startCmd = Mockito.mock(StartContainerCmd.class);

        when(containerManager.findByName("squad-sess1-agent1")).thenReturn(Optional.empty());
        when(dockerClient.createContainerCmd("agent-image")).thenReturn(createCmd);
        when(createCmd.withName("squad-sess1-agent1")).thenReturn(createCmd);
        when(createCmd.withNetworkMode("squad-network")).thenReturn(createCmd);
        when(createCmd.withEnv(List.of("A=B"))).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(response);
        when(response.getId()).thenReturn("cid-1");
        when(dockerClient.startContainerCmd("cid-1")).thenReturn(startCmd);

        ContainerLifecycleManager manager = new ContainerLifecycleManager(
                dockerClient,
                containerManager,
                "agent-image",
                "squad-network"
        );

        String id = manager.createAndStartContainer("sess1", "agent1", List.of("A=B"));

        assertThat(id).isEqualTo("cid-1");
        verify(startCmd).exec();
    }

    @Test
    void 동일_이름이_있으면_삭제한다() {
        DockerClient dockerClient = Mockito.mock(DockerClient.class);
        DockerContainerManager containerManager = Mockito.mock(DockerContainerManager.class);
        CreateContainerCmd createCmd = Mockito.mock(CreateContainerCmd.class);
        CreateContainerResponse response = Mockito.mock(CreateContainerResponse.class);
        RemoveContainerCmd removeCmd = Mockito.mock(RemoveContainerCmd.class);
        Container existing = Mockito.mock(Container.class);

        when(existing.getId()).thenReturn("old-id");
        when(containerManager.findByName("squad-sess1-agent1")).thenReturn(Optional.of(existing));
        when(dockerClient.removeContainerCmd("old-id")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);

        when(dockerClient.createContainerCmd("agent-image")).thenReturn(createCmd);
        when(createCmd.withName("squad-sess1-agent1")).thenReturn(createCmd);
        when(createCmd.withNetworkMode("squad-network")).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(response);
        when(response.getId()).thenReturn("cid-1");

        ContainerLifecycleManager manager = new ContainerLifecycleManager(
                dockerClient,
                containerManager,
                "agent-image",
                "squad-network"
        );

        String id = manager.createContainer("sess1", "agent1", List.of());

        assertThat(id).isEqualTo("cid-1");
        verify(removeCmd).exec();
    }

    @Test
    void 컨테이너를_중지하고_삭제한다() {
        DockerClient dockerClient = Mockito.mock(DockerClient.class);
        DockerContainerManager containerManager = Mockito.mock(DockerContainerManager.class);
        StopContainerCmd stopCmd = Mockito.mock(StopContainerCmd.class);
        RemoveContainerCmd removeCmd = Mockito.mock(RemoveContainerCmd.class);

        when(dockerClient.stopContainerCmd("cid-1")).thenReturn(stopCmd);
        when(stopCmd.withTimeout(10)).thenReturn(stopCmd);
        when(dockerClient.removeContainerCmd("cid-1")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);

        ContainerLifecycleManager manager = new ContainerLifecycleManager(
                dockerClient,
                containerManager,
                "agent-image",
                "squad-network"
        );

        manager.stopAndRemoveContainer("cid-1");

        verify(stopCmd).exec();
        verify(removeCmd).exec();
    }

    @Test
    void 이미지가_없으면_가이드_메시지와_함께_실패한다() {
        DockerClient dockerClient = Mockito.mock(DockerClient.class);
        DockerContainerManager containerManager = Mockito.mock(DockerContainerManager.class);
        CreateContainerCmd createCmd = Mockito.mock(CreateContainerCmd.class);

        when(containerManager.findByName("squad-sess1-agent1")).thenReturn(Optional.empty());
        when(dockerClient.createContainerCmd("squad-agent:latest")).thenReturn(createCmd);
        when(createCmd.withName("squad-sess1-agent1")).thenReturn(createCmd);
        when(createCmd.withNetworkMode("squad-network")).thenReturn(createCmd);
        when(createCmd.exec()).thenThrow(new NotFoundException("No such image"));

        ContainerLifecycleManager manager = new ContainerLifecycleManager(
                dockerClient,
                containerManager,
                "squad-agent:latest",
                "squad-network"
        );

        assertThatThrownBy(() -> manager.createContainer("sess1", "agent1", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agent 이미지가 없습니다")
                .hasMessageContaining("docker build -f docker/agent/Dockerfile -t squad-agent:latest .");
    }
}
