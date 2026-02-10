package com.squad.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 설정.
 *
 * <p>클라이언트가 세션 진행 상황을 실시간으로 수신할 수 있도록
 * WebSocket 엔드포인트와 메시지 브로커를 구성한다.</p>
 *
 * <ul>
 *   <li>WebSocket 엔드포인트: {@code /ws}</li>
 *   <li>구독 경로: {@code /topic/sessions/{sessionId}}</li>
 *   <li>Heartbeat: 서버→클라이언트 10초, 클라이언트→서버 10초</li>
 * </ul>
 *
 * @see SessionEvent
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_INTERVAL = 10_000L;

    @Value("${squad.websocket.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL});
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);
    }
}
