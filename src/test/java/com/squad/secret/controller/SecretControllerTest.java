package com.squad.secret.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.secret.dto.SecretCreateRequest;
import com.squad.secret.dto.SecretResponse;
import com.squad.secret.dto.SecretUpdateRequest;
import com.squad.secret.service.SecretService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SecretController.class)
class SecretControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SecretService secretService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private SecretResponse sampleResponse() {
        return new SecretResponse(1L, "github-token", NOW, NOW);
    }

    @Test
    void Secret_목록_조회_시_value_포함되지_않고_성공_응답_반환() throws Exception {
        given(secretService.findAll()).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/secrets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("github-token"))
                .andExpect(jsonPath("$.data[0].value").doesNotExist());
    }

    @Test
    void Secret_ID로_조회_시_value_포함되지_않고_성공_응답_반환() throws Exception {
        given(secretService.findById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/secrets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("github-token"))
                .andExpect(jsonPath("$.data.value").doesNotExist());
    }

    @Test
    void 존재하지_않는_Secret_조회_시_404_응답() throws Exception {
        given(secretService.findById(99L)).willThrow(new NotFoundException(ErrorCode.SECRET_NOT_FOUND));

        mockMvc.perform(get("/api/v1/secrets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SECRET_NOT_FOUND"));
    }

    @Test
    void Secret_생성_시_201_응답_반환() throws Exception {
        SecretCreateRequest request = new SecretCreateRequest("github-token", "ghp_abc123secret");
        given(secretService.create(any(SecretCreateRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/secrets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.value").doesNotExist())
                .andExpect(jsonPath("$.message").value("Secret이 생성되었습니다."));
    }

    @Test
    void Secret_생성_시_name_빈_값_400_응답() throws Exception {
        SecretCreateRequest request = new SecretCreateRequest("", "some-value");

        mockMvc.perform(post("/api/v1/secrets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void Secret_생성_시_value_빈_값_400_응답() throws Exception {
        SecretCreateRequest request = new SecretCreateRequest("test-secret", "");

        mockMvc.perform(post("/api/v1/secrets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void Secret_수정_시_성공_응답_반환() throws Exception {
        SecretUpdateRequest request = new SecretUpdateRequest("ghp_updated_value");
        SecretResponse response = new SecretResponse(1L, "github-token", NOW, NOW);
        given(secretService.update(eq(1L), any(SecretUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/v1/secrets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.value").doesNotExist());
    }

    @Test
    void 존재하지_않는_Secret_수정_시_404_응답() throws Exception {
        SecretUpdateRequest request = new SecretUpdateRequest("value");
        given(secretService.update(eq(99L), any(SecretUpdateRequest.class)))
                .willThrow(new NotFoundException(ErrorCode.SECRET_NOT_FOUND));

        mockMvc.perform(put("/api/v1/secrets/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SECRET_NOT_FOUND"));
    }

    @Test
    void Secret_삭제_시_성공_응답_반환() throws Exception {
        willDoNothing().given(secretService).delete(1L);

        mockMvc.perform(delete("/api/v1/secrets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Secret이 삭제되었습니다."));
    }

    @Test
    void 존재하지_않는_Secret_삭제_시_404_응답() throws Exception {
        willThrow(new NotFoundException(ErrorCode.SECRET_NOT_FOUND)).given(secretService).delete(99L);

        mockMvc.perform(delete("/api/v1/secrets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SECRET_NOT_FOUND"));
    }

    @Test
    void Secret_참조_해결_시_복호화된_값_반환() throws Exception {
        given(secretService.resolveSecret("ref:secret/github-token")).willReturn("ghp_abc123secret");

        mockMvc.perform(get("/api/v1/secrets/resolve")
                        .param("ref", "ref:secret/github-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ghp_abc123secret"));
    }

    @Test
    void Secret_참조_해결_시_존재하지_않는_secret_404_응답() throws Exception {
        given(secretService.resolveSecret("ref:secret/missing"))
                .willThrow(new NotFoundException(ErrorCode.SECRET_NOT_FOUND));

        mockMvc.perform(get("/api/v1/secrets/resolve")
                        .param("ref", "ref:secret/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SECRET_NOT_FOUND"));
    }

    @Test
    void Secret_참조_해결_시_잘못된_ref_형식_400_응답() throws Exception {
        given(secretService.resolveSecret("invalid-ref"))
                .willThrow(new ValidationException(ErrorCode.INVALID_REQUEST, "참조 형식은 'ref:secret/<name>'이어야 합니다."));

        mockMvc.perform(get("/api/v1/secrets/resolve")
                        .param("ref", "invalid-ref"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }
}
