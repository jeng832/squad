package com.squad.agent.runner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Duration;

/**
 * Docker 클라이언트 설정.
 * zerodep 트랜스포트를 사용하여 외부 HTTP 클라이언트 의존성 충돌을 방지합니다.
 */
@Configuration
public class DockerClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DockerClientConfiguration.class);

    @Bean
    public DockerClient dockerClient(
            @Value("${squad.docker.host:unix:///var/run/docker.sock}") String dockerHost
    ) {
        log.info("Docker host 설정: {}", dockerHost);

        DockerClientConfig config = new DefaultDockerClientConfig.Builder()
                .withDockerHost(dockerHost)
                .build();

        log.info("DockerClientConfig dockerHost: {}", config.getDockerHost());

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerHost))
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
