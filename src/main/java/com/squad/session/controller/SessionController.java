package com.squad.session.controller;

import com.squad.common.api.ApiResponse;
import com.squad.session.dto.SessionCreateRequest;
import com.squad.session.dto.SessionResponse;
import com.squad.session.service.SessionExecutionService;
import com.squad.session.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 세션 REST API 컨트롤러.
 *
 * <p>세션의 CRUD(생성, 조회, 취소)와 실행(시작) 엔드포인트를 제공한다.</p>
 *
 * <h3>{@code SessionExecutionService}의 선택적 주입 ({@code @Autowired(required = false)})</h3>
 * <p>{@link SessionExecutionService}는 {@code @ConditionalOnBean(MessagePublisher.class)}가
 * 적용되어 있어, 메시징 인프라가 비활성화된 환경에서는 스프링 컨테이너에 등록되지 않는다.
 * 이 경우 일반적인 필수 주입({@code required = true})을 사용하면 컨텍스트 로딩 시
 * {@code NoSuchBeanDefinitionException}이 발생하여 CRUD API를 포함한 컨트롤러 전체가
 * 사용 불가능해진다.</p>
 *
 * <p>이를 방지하기 위해 {@code @Autowired(required = false)}로 선택적 주입을 적용했다.
 * 메시징이 비활성화되면 {@code sessionExecutionService}에 {@code null}이 주입되어,
 * CRUD API(목록 조회, 단건 조회, 생성, 취소)는 정상 동작하고,
 * 세션 시작 API({@code POST /{id}/start}) 호출 시에만
 * 503 Service Unavailable 응답을 반환한다.</p>
 *
 * @see SessionExecutionService
 * @see SessionService
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionExecutionService sessionExecutionService;

    /**
     * @param sessionService          세션 CRUD 서비스 (필수)
     * @param sessionExecutionService 세션 실행 서비스 (선택적 — 메시징 비활성화 시 {@code null})
     */
    public SessionController(SessionService sessionService,
                             @Autowired(required = false) SessionExecutionService sessionExecutionService) {
        this.sessionService = sessionService;
        this.sessionExecutionService = sessionExecutionService;
    }

    @GetMapping
    public ApiResponse<List<SessionResponse>> findAll() {
        return ApiResponse.success(sessionService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(sessionService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SessionResponse>> create(@Valid @RequestBody SessionCreateRequest request) {
        SessionResponse response = sessionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "세션이 생성되었습니다."));
    }

    /**
     * 세션을 시작한다.
     *
     * <p>메시징 인프라가 비활성화되어 {@code SessionExecutionService} 빈이 존재하지 않는 경우
     * 503 Service Unavailable 응답을 반환한다.</p>
     *
     * @param id 시작할 세션 ID
     * @return 시작된 세션 정보
     */
    @PostMapping("/{id}/start")
    public ApiResponse<SessionResponse> start(@PathVariable Long id) {
        if (sessionExecutionService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "메시징이 비활성화되어 세션을 시작할 수 없습니다.");
        }
        return ApiResponse.success(sessionExecutionService.start(id), "세션이 시작되었습니다.");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<SessionResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(sessionService.cancel(id), "세션이 취소되었습니다.");
    }
}
