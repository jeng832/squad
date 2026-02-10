package com.squad.session.controller;

import com.squad.common.api.ApiResponse;
import com.squad.session.dto.SessionCreateRequest;
import com.squad.session.dto.SessionResponse;
import com.squad.session.service.SessionExecutionService;
import com.squad.session.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionExecutionService sessionExecutionService;

    public SessionController(SessionService sessionService,
                             SessionExecutionService sessionExecutionService) {
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

    @PostMapping("/{id}/start")
    public ApiResponse<SessionResponse> start(@PathVariable Long id) {
        return ApiResponse.success(sessionExecutionService.start(id), "세션이 시작되었습니다.");
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<SessionResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(sessionService.cancel(id), "세션이 취소되었습니다.");
    }
}
