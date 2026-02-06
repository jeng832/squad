package com.squad.secret.controller;

import com.squad.common.api.ApiResponse;
import com.squad.secret.dto.SecretCreateRequest;
import com.squad.secret.dto.SecretResponse;
import com.squad.secret.dto.SecretUpdateRequest;
import com.squad.secret.service.SecretService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/secrets")
public class SecretController {

    private final SecretService secretService;

    public SecretController(SecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping
    public ApiResponse<List<SecretResponse>> findAll() {
        return ApiResponse.success(secretService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SecretResponse> findById(@PathVariable Long id) {
        return ApiResponse.success(secretService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SecretResponse>> create(@Valid @RequestBody SecretCreateRequest request) {
        SecretResponse response = secretService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Secret이 생성되었습니다."));
    }

    @PutMapping("/{id}")
    public ApiResponse<SecretResponse> update(@PathVariable Long id, @Valid @RequestBody SecretUpdateRequest request) {
        return ApiResponse.success(secretService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable Long id) {
        secretService.delete(id);
        return ApiResponse.success(null, "Secret이 삭제되었습니다.");
    }

    /**
     * {@code ref:secret/<name>} 형식의 참조를 해결하여 복호화된 값을 반환합니다.
     *
     * <p>사용 예: {@code GET /api/v1/secrets/resolve?ref=ref:secret/github-token}</p>
     */
    @GetMapping("/resolve")
    public ApiResponse<String> resolve(@RequestParam String ref) {
        return ApiResponse.success(secretService.resolveSecret(ref));
    }
}
