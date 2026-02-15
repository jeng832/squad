package com.squad.mcp.process;

import com.squad.secret.service.SecretService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 환경변수에서 {@code ref:secret/<name>} 참조를 해결하는 컴포넌트.
 *
 * <p>환경변수 값이 {@code ref:secret/} 접두사로 시작하면
 * {@link SecretService}를 통해 복호화된 실제 값으로 치환한다.
 * 그 외의 값은 그대로 유지한다.</p>
 *
 * <p>Secret 해결 실패 시 Fail-Closed 정책을 따라
 * 예외를 전파하여 MCP 프로세스 시작을 차단한다.</p>
 *
 * @see SecretService
 * @see McpConfig
 */
@Slf4j
@Component
public class EnvResolver {

    private static final String REF_SECRET_PREFIX = "ref:secret/";

    private final SecretService secretService;

    public EnvResolver(SecretService secretService) {
        this.secretService = secretService;
    }

    /**
     * 환경변수 Map의 {@code ref:secret/} 참조를 해결한다.
     *
     * <p>{@code ref:secret/} 접두사가 있는 값은 {@link SecretService#resolveSecret(String)}을
     * 통해 복호화된 값으로 치환하고, 나머지는 그대로 유지한다.</p>
     *
     * @param env 원본 환경변수 Map
     * @return 참조가 해결된 환경변수 Map (불변)
     * @throws com.squad.common.exception.NotFoundException 참조된 Secret이 존재하지 않는 경우
     * @throws com.squad.common.exception.ValidationException 참조 형식이 올바르지 않은 경우
     */
    public Map<String, String> resolve(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return Map.of();
        }

        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (isSecretRef(value)) {
                log.debug("Secret 참조 해결: key={}", key);
                String resolvedValue = secretService.resolveSecret(value);
                resolved.put(key, resolvedValue);
            } else {
                resolved.put(key, value);
            }
        }

        return Collections.unmodifiableMap(resolved);
    }

    private boolean isSecretRef(String value) {
        return value != null && value.startsWith(REF_SECRET_PREFIX);
    }
}
