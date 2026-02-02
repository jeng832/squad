package com.squad;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Squad 애플리케이션 컨텍스트 로드 테스트.
 */
@SpringBootTest
@ActiveProfiles("test")
class SquadApplicationTests {

    @Test
    void contextLoads() {
        // 애플리케이션 컨텍스트가 정상적으로 로드되는지 확인
    }
}
