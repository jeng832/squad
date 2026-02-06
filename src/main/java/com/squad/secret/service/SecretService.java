package com.squad.secret.service;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.secret.domain.Secret;
import com.squad.secret.dto.SecretCreateRequest;
import com.squad.secret.dto.SecretResponse;
import com.squad.secret.dto.SecretUpdateRequest;
import com.squad.secret.repository.SecretRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SecretService {

    private static final String REF_PREFIX = "ref:secret/";

    private final SecretRepository secretRepository;
    private final AesEncryptionUtil encryptionUtil;

    public SecretService(SecretRepository secretRepository, AesEncryptionUtil encryptionUtil) {
        this.secretRepository = secretRepository;
        this.encryptionUtil = encryptionUtil;
    }

    public List<SecretResponse> findAll() {
        return secretRepository.findAll().stream()
                .map(SecretResponse::from)
                .toList();
    }

    public SecretResponse findById(Long id) {
        Secret secret = secretRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SECRET_NOT_FOUND));
        return SecretResponse.from(secret);
    }

    @Transactional
    public SecretResponse create(SecretCreateRequest request) {
        String encryptedValue = encryptionUtil.encrypt(request.value());
        Secret secret = Secret.builder()
                .name(request.name())
                .value(encryptedValue)
                .build();
        return SecretResponse.from(secretRepository.save(secret));
    }

    @Transactional
    public SecretResponse update(Long id, SecretUpdateRequest request) {
        Secret secret = secretRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SECRET_NOT_FOUND));
        secret.update(encryptionUtil.encrypt(request.value()));
        return SecretResponse.from(secret);
    }

    @Transactional
    public void delete(Long id) {
        secretRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SECRET_NOT_FOUND));
        secretRepository.deleteById(id);
    }

    /**
     * {@code ref:secret/<name>} 형식의 참조를 해결하여 복호화된 값을 반환합니다.
     *
     * @param ref 참조 문자열 (예: {@code ref:secret/github-token})
     * @return 복호화된 Secret 값
     * @throws ValidationException  ref 형식이 올바르지 않은 경우
     * @throws NotFoundException    해당 name의 Secret이 존재하지 않는 경우
     */
    public String resolveSecret(String ref) {
        if (ref == null || !ref.startsWith(REF_PREFIX)) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "참조 형식은 'ref:secret/<name>'이어야 합니다.");
        }

        String name = ref.substring(REF_PREFIX.length());
        if (name.isBlank()) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST, "참조 형식은 'ref:secret/<name>'이어야 합니다.");
        }

        Secret secret = secretRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SECRET_NOT_FOUND));
        return encryptionUtil.decrypt(secret.getValue());
    }
}
