package com.squad.mcp.process;

import com.squad.common.exception.NotFoundException;
import com.squad.secret.service.SecretService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EnvResolver 단위 테스트")
@ExtendWith(MockitoExtension.class)
class EnvResolverTest {

    @Mock
    private SecretService secretService;

    private EnvResolver envResolver;

    @BeforeEach
    void setUp() {
        envResolver = new EnvResolver(secretService);
    }

    @Test
    @DisplayName("일반 환경변수는 그대로 유지한다")
    void resolvePassesThroughPlainValues() {
        Map<String, String> env = Map.of("PATH", "/usr/bin", "HOME", "/home/user");

        Map<String, String> resolved = envResolver.resolve(env);

        assertThat(resolved).containsEntry("PATH", "/usr/bin");
        assertThat(resolved).containsEntry("HOME", "/home/user");
    }

    @Test
    @DisplayName("ref:secret/ 참조를 복호화된 값으로 치환한다")
    void resolveReplacesSecretRef() {
        Map<String, String> env = Map.of("GITHUB_TOKEN", "ref:secret/github-token");
        when(secretService.resolveSecret("ref:secret/github-token")).thenReturn("actual-token-value");

        Map<String, String> resolved = envResolver.resolve(env);

        assertThat(resolved).containsEntry("GITHUB_TOKEN", "actual-token-value");
        verify(secretService).resolveSecret("ref:secret/github-token");
    }

    @Test
    @DisplayName("일반 값과 Secret 참조가 혼재된 env를 올바르게 처리한다")
    void resolveMixedEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("NODE_ENV", "production");
        env.put("API_KEY", "ref:secret/api-key");
        env.put("DEBUG", "false");

        when(secretService.resolveSecret("ref:secret/api-key")).thenReturn("real-api-key");

        Map<String, String> resolved = envResolver.resolve(env);

        assertThat(resolved).hasSize(3);
        assertThat(resolved).containsEntry("NODE_ENV", "production");
        assertThat(resolved).containsEntry("API_KEY", "real-api-key");
        assertThat(resolved).containsEntry("DEBUG", "false");
    }

    @Test
    @DisplayName("존재하지 않는 Secret 참조 시 예외를 전파한다 (Fail-Closed)")
    void resolveThrowsWhenSecretNotFound() {
        Map<String, String> env = Map.of("TOKEN", "ref:secret/nonexistent");
        when(secretService.resolveSecret("ref:secret/nonexistent"))
                .thenThrow(new NotFoundException(com.squad.common.exception.ErrorCode.SECRET_NOT_FOUND));

        assertThatThrownBy(() -> envResolver.resolve(env))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("null env는 빈 Map을 반환한다")
    void resolveNullEnvReturnsEmptyMap() {
        Map<String, String> resolved = envResolver.resolve(null);

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("빈 env는 빈 Map을 반환한다")
    void resolveEmptyEnvReturnsEmptyMap() {
        Map<String, String> resolved = envResolver.resolve(Map.of());

        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("ref:secret/ 접두사가 아닌 ref: 값은 그대로 유지한다")
    void resolveDoesNotTouchNonSecretRefs() {
        Map<String, String> env = Map.of("VALUE", "ref:other/something");

        Map<String, String> resolved = envResolver.resolve(env);

        assertThat(resolved).containsEntry("VALUE", "ref:other/something");
        verify(secretService, never()).resolveSecret("ref:other/something");
    }
}
