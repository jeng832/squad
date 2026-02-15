package com.squad.secret.service;

import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.secret.domain.Secret;
import com.squad.secret.dto.SecretCreateRequest;
import com.squad.secret.dto.SecretResponse;
import com.squad.secret.dto.SecretUpdateRequest;
import com.squad.secret.repository.SecretRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretService 단위 테스트")
class SecretServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private AesEncryptionUtil encryptionUtil;

    @InjectMocks
    private SecretService secretService;

    private Secret createSecret(Long id, String name) {
        return Secret.builder()
                .id(id)
                .name(name)
                .value("encrypted-value")
                .build();
    }

    @Test
    @DisplayName("전체 Secret 목록을 조회한다")
    void findAll() {
        given(secretRepository.findAll()).willReturn(List.of(
                createSecret(1L, "secret-1"), createSecret(2L, "secret-2")));

        List<SecretResponse> result = secretService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("secret-1");
    }

    @Test
    @DisplayName("빈 목록 조회 시 빈 리스트를 반환한다")
    void findAllEmpty() {
        given(secretRepository.findAll()).willReturn(List.of());

        assertThat(secretService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ID로 Secret을 조회한다")
    void findById() {
        given(secretRepository.findById(1L)).willReturn(Optional.of(createSecret(1L, "secret-1")));

        SecretResponse result = secretService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("secret-1");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 NotFoundException 발생")
    void findByIdNotFound() {
        given(secretRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.findById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Secret을 생성한다")
    void create() {
        given(encryptionUtil.encrypt("plain-value")).willReturn("encrypted");
        Secret saved = createSecret(1L, "new-secret");
        given(secretRepository.save(any(Secret.class))).willReturn(saved);

        SecretCreateRequest request = new SecretCreateRequest("new-secret", "plain-value");
        SecretResponse result = secretService.create(request);

        assertThat(result.name()).isEqualTo("new-secret");
        verify(encryptionUtil).encrypt("plain-value");
        verify(secretRepository).save(any(Secret.class));
    }

    @Test
    @DisplayName("Secret을 수정한다")
    void update() {
        Secret secret = createSecret(1L, "secret-1");
        given(secretRepository.findById(1L)).willReturn(Optional.of(secret));
        given(encryptionUtil.encrypt("new-value")).willReturn("new-encrypted");

        SecretUpdateRequest request = new SecretUpdateRequest("new-value");
        SecretResponse result = secretService.update(1L, request);

        assertThat(result.name()).isEqualTo("secret-1");
        assertThat(secret.getValue()).isEqualTo("new-encrypted");
        verify(encryptionUtil).encrypt("new-value");
    }

    @Test
    @DisplayName("존재하지 않는 Secret 수정 시 NotFoundException 발생")
    void updateNotFound() {
        given(secretRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.update(99L, new SecretUpdateRequest("value")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Secret을 삭제한다")
    void delete() {
        given(secretRepository.findById(1L)).willReturn(Optional.of(createSecret(1L, "secret-1")));

        secretService.delete(1L);

        verify(secretRepository).deleteById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 Secret 삭제 시 NotFoundException 발생")
    void deleteNotFound() {
        given(secretRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("유효한 ref 형식으로 Secret을 조회하고 복호화한다")
    void resolveSecret() {
        Secret secret = createSecret(1L, "github-token");
        given(secretRepository.findByName("github-token")).willReturn(Optional.of(secret));
        given(encryptionUtil.decrypt("encrypted-value")).willReturn("decrypted-value");

        String result = secretService.resolveSecret("ref:secret/github-token");

        assertThat(result).isEqualTo("decrypted-value");
        verify(encryptionUtil).decrypt("encrypted-value");
    }

    @Test
    @DisplayName("ref가 null이면 ValidationException 발생")
    void resolveSecretNullRef() {
        assertThatThrownBy(() -> secretService.resolveSecret(null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("ref가 올바른 prefix로 시작하지 않으면 ValidationException 발생")
    void resolveSecretInvalidPrefix() {
        assertThatThrownBy(() -> secretService.resolveSecret("invalid/github-token"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("ref에서 name 부분이 빈 문자열이면 ValidationException 발생")
    void resolveSecretBlankName() {
        assertThatThrownBy(() -> secretService.resolveSecret("ref:secret/"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("ref로 조회한 Secret이 존재하지 않으면 NotFoundException 발생")
    void resolveSecretNotFound() {
        given(secretRepository.findByName("non-existent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> secretService.resolveSecret("ref:secret/non-existent"))
                .isInstanceOf(NotFoundException.class);
    }
}
