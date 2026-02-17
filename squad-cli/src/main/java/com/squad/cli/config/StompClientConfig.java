package com.squad.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * STOMP WebSocket 클라이언트 설정.
 *
 * <p>CLI에서 서버의 WebSocket 엔드포인트({@code /ws})에 STOMP로 연결하기 위한
 * {@link WebSocketStompClient}를 구성한다.</p>
 *
 * <p>서버가 SockJS 없이 순수 WebSocket STOMP를 사용하므로
 * {@link StandardWebSocketClient}를 직접 사용한다.</p>
 */
@Configuration
public class StompClientConfig {

    /**
     * STOMP WebSocket 클라이언트 빈을 생성한다.
     *
     * <p>{@link JavaTimeModule}을 등록한 {@link ObjectMapper}를 사용하여
     * 서버에서 보내는 {@code SessionEvent}의 {@code LocalDateTime} 필드를
     * 올바르게 역직렬화한다.</p>
     *
     * @return WebSocketStompClient 인스턴스
     */
    @Bean
    public WebSocketStompClient webSocketStompClient() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(converter);
        return stompClient;
    }
}
