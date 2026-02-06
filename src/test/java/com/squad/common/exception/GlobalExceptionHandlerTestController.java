package com.squad.common.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 전용 컨트롤러.
 *
 * <p>GlobalExceptionHandler의 각 핸들러가 기대한 상태 코드와 응답을 반환하는지 검증하기 위한 엔드포인트를 제공합니다.</p>
 */
@RestController
@RequestMapping("/test")
class GlobalExceptionHandlerTestController {

    @GetMapping("/squad-exception")
    public void throwSquadException() {
        throw new SquadException(ErrorCode.AGENT_NOT_FOUND);
    }

    @GetMapping("/not-found")
    public void throwNotFoundException() {
        throw new NotFoundException(ErrorCode.AGENT_NOT_FOUND, "테스트 에이전트 미발견");
    }

    @GetMapping("/validation")
    public void throwValidationException() {
        throw new ValidationException(ErrorCode.ORCHESTRATOR_REQUIRED);
    }

    @GetMapping("/exception")
    public void throwException() throws Exception {
        throw new Exception("일반 예외");
    }
}
