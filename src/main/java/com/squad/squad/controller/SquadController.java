package com.squad.squad.controller;

import com.squad.common.api.ApiResponse;
import com.squad.squad.dto.SquadCreateRequest;
import com.squad.squad.dto.SquadResponse;
import com.squad.squad.dto.SquadUpdateRequest;
import com.squad.squad.service.SquadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/squads")
public class SquadController {

    private final SquadService squadService;

    public SquadController(SquadService squadService) {
        this.squadService = squadService;
    }

    @GetMapping
    public ApiResponse<List<SquadResponse>> findAll() {
        return ApiResponse.success(squadService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SquadResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(squadService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SquadResponse>> create(@Valid @RequestBody SquadCreateRequest request) {
        SquadResponse response = squadService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Squad가 생성되었습니다."));
    }

    @PutMapping("/{id}")
    public ApiResponse<SquadResponse> update(@PathVariable Long id, @Valid @RequestBody SquadUpdateRequest request) {
        return ApiResponse.success(squadService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        squadService.delete(id);
        return ApiResponse.success(null, "Squad가 삭제되었습니다.");
    }
}
