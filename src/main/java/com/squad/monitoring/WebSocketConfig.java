package com.squad.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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

    private final TaskScheduler heartbeatScheduler;

    public WebSocketConfig(TaskScheduler heartbeatScheduler) {
        this.heartbeatScheduler = heartbeatScheduler;
    }

    /**
     * Heartbeat 전송용 TaskScheduler 빈.
     *
     * <p>Spring Context에 의해 관리되므로 앱 종료 시 스레드가 정상 정리된다.</p>
     *
     * @return heartbeat용 TaskScheduler
     */
    @Bean
    public static TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);
    }
}
