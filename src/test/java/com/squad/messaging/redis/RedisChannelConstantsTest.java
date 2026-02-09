package com.squad.messaging.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RedisChannelConstants 테스트")
class RedisChannelConstantsTest {

    @Test
    @DisplayName("Orchestrator 채널명을 올바르게 생성한다")
    void orchestratorChannel() {
        String channel = RedisChannelConstants.orchestratorChannel(1L);

        assertThat(channel).isEqualTo("session:1:orchestrator");
    }

    @Test
    @DisplayName("Agent 채널명을 올바르게 생성한다")
    void agentChannel() {
        String channel = RedisChannelConstants.agentChannel(1L, 5L);

        assertThat(channel).isEqualTo("session:1:agent:5");
    }

    @Test
    @DisplayName("브로드캐스트 채널명을 올바르게 생성한다")
    void broadcastChannel() {
        String channel = RedisChannelConstants.broadcastChannel(1L);

        assertThat(channel).isEqualTo("session:1:broadcast");
    }

    @Test
    @DisplayName("세션 패턴을 올바르게 생성한다")
    void sessionPattern() {
        String pattern = RedisChannelConstants.sessionPattern(42L);

        assertThat(pattern).isEqualTo("session:42:*");
    }

    @Test
    @DisplayName("서로 다른 세션은 서로 다른 채널을 갖는다")
    void differentSessionsDifferentChannels() {
        String channel1 = RedisChannelConstants.orchestratorChannel(1L);
        String channel2 = RedisChannelConstants.orchestratorChannel(2L);

        assertThat(channel1).isNotEqualTo(channel2);
    }

    @Test
    @DisplayName("같은 세션의 서로 다른 Agent는 서로 다른 채널을 갖는다")
    void differentAgentsDifferentChannels() {
        String channel1 = RedisChannelConstants.agentChannel(1L, 1L);
        String channel2 = RedisChannelConstants.agentChannel(1L, 2L);

        assertThat(channel1).isNotEqualTo(channel2);
    }
}
