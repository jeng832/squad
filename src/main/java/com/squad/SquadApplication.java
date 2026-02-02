package com.squad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Squad 애플리케이션 진입점.
 *
 * <p>멀티 AI 에이전트 협업 플랫폼의 메인 서버 애플리케이션입니다.</p>
 */
@SpringBootApplication
public class SquadApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadApplication.class, args);
    }
}
