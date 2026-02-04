package com.squad.agent.controller;

import com.squad.agent.dto.AgentCreateRequest;
import com.squad.agent.dto.AgentResponse;
import com.squad.agent.dto.AgentUpdateRequest;
import com.squad.agent.service.AgentService;
import com.squad.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    public ApiResponse<List<AgentResponse>> findAll() {
        return ApiResponse.success(agentService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(agentService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AgentResponse>> create(@Valid @RequestBody AgentCreateRequest request) {
        AgentResponse response = agentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "에이전트가 생성되었습니다."));
    }

    @PutMapping("/{id}")
    public ApiResponse<AgentResponse> update(@PathVariable Long id, @Valid @RequestBody AgentUpdateRequest request) {
        return ApiResponse.success(agentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        agentService.delete(id);
        return ApiResponse.success(null, "에이전트가 삭제되었습니다.");
    }
}
