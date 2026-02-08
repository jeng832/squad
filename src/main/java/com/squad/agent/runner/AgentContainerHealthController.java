package com.squad.agent.runner;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent Container 상태 확인용 엔드포인트.
 */
@RestController
@RequestMapping("/health")
public class AgentContainerHealthController {

    @GetMapping
    public String health() {
        return "ok";
    }
}
