package com.squad.secret.repository;

import com.squad.secret.domain.Secret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class SecretRepositoryTest {

    @Autowired
    private SecretRepository secretRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Secret buildSecret(String name, String value) {
        return Secret.builder()
                .name(name)
                .value(value)
                .build();
    }

    @Test
    void Secret_저장_후_ID로_조회_시_저장된_Secret_반환() {
        Secret secret = buildSecret("db-password", "encrypted-value-abc");

        Secret saved = entityManager.persistFlushPop(secret);

        Secret found = secretRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("db-password");
        assertThat(found.getValue()).isEqualTo("encrypted-value-abc");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void Secret_이름으로_조회_시_해당_Secret_반환() {
        entityManager.persistFlushPop(buildSecret("db-password", "value1"));
        entityManager.persistFlushPop(buildSecret("api-key", "value2"));

        Optional<Secret> found = secretRepository.findByName("api-key");

        assertThat(found).isPresent();
        assertThat(found.get().getValue()).isEqualTo("value2");
    }

    @Test
    void Secret_존재하지_않는_이름으로_조회_시_빈_결과_반환() {
        Optional<Secret> found = secretRepository.findByName("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void Secret_정보_수정_후_저장_시_수정된_값_반환() {
        Secret secret = entityManager.persistFlushPop(buildSecret("db-password", "old-value"));

        Secret loaded = secretRepository.findById(secret.getId()).orElseThrow();
        loaded.update("new-encrypted-value");
        secretRepository.flush();

        Secret found = secretRepository.findById(secret.getId()).orElseThrow();

        assertThat(found.getValue()).isEqualTo("new-encrypted-value");
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 중복_이름으로_저장_시_예외_발생() {
        entityManager.persistFlushPop(buildSecret("db-password", "value1"));

        assertThatThrownBy(() -> {
            secretRepository.save(buildSecret("db-password", "value2"));
            secretRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
