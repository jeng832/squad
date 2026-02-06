package com.squad.skill.controller;

import com.squad.common.api.ApiResponse;
import com.squad.skill.dto.SkillCreateRequest;
import com.squad.skill.dto.SkillResponse;
import com.squad.skill.dto.SkillUpdateRequest;
import com.squad.skill.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ApiResponse<List<SkillResponse>> findAll() {
        return ApiResponse.success(skillService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SkillResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(skillService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillResponse>> create(@Valid @RequestBody SkillCreateRequest request) {
        SkillResponse response = skillService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Skill이 생성되었습니다."));
    }

    @PutMapping("/{id}")
    public ApiResponse<SkillResponse> update(@PathVariable Long id, @Valid @RequestBody SkillUpdateRequest request) {
        return ApiResponse.success(skillService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        skillService.delete(id);
        return ApiResponse.success(null, "Skill이 삭제되었습니다.");
    }
}
