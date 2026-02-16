package com.squad.cli.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Squad 서버 REST API 호출 클라이언트.
 *
 * <p>RestClient를 사용하여 서버의 각 리소스 엔드포인트와 통신한다.
 * 모든 응답은 {@link JsonNode}로 반환하며, 호출자가 필요에 따라 파싱한다.</p>
 */
@Component
public class SquadApiClient {

    private final RestClient restClient;

    public SquadApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * GET 요청을 수행한다.
     *
     * @param path API 경로 (예: "/api/squads")
     * @return 응답 JSON
     */
    public Optional<JsonNode> get(String path) {
        try {
            JsonNode response = restClient.get()
                    .uri(path)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    /**
     * POST 요청을 수행한다.
     *
     * @param path API 경로
     * @param body 요청 본문
     * @return 응답 JSON
     */
    public Optional<JsonNode> post(String path, Object body) {
        try {
            JsonNode response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    /**
     * PUT 요청을 수행한다.
     *
     * @param path API 경로
     * @param body 요청 본문
     * @return 응답 JSON
     */
    public Optional<JsonNode> put(String path, Object body) {
        try {
            JsonNode response = restClient.put()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    /**
     * DELETE 요청을 수행한다.
     *
     * @param path API 경로
     * @return 성공 여부
     */
    public boolean delete(String path) {
        try {
            restClient.delete()
                    .uri(path)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /**
     * 서버 연결 상태를 확인한다.
     *
     * @return 연결 가능 여부
     */
    public boolean isServerReachable() {
        try {
            restClient.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }
}
