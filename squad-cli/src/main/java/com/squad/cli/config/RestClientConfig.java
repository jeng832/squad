package com.squad.cli.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * RestClient 빈 설정.
 *
 * <p>Java 21 Virtual Threads 기반 HttpClient를 사용하여
 * Squad 서버와 REST API 통신을 수행한다.</p>
 */
@Configuration
public class RestClientConfig {

    /**
     * Squad 서버 통신용 RestClient 빈을 생성한다.
     *
     * @param cliConfig CLI 설정 (서버 URL 포함)
     * @return RestClient 인스턴스
     */
    @Bean
    public RestClient restClient(CliConfig cliConfig) {
        HttpClient httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        return RestClient.builder()
                .baseUrl(cliConfig.getServerUrl())
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }
}
