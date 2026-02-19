package com.squad.session.service;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.secret.repository.SecretRepository;
import com.squad.session.domain.GitProvider;
import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import com.squad.session.dto.SessionCreateRequest;
import com.squad.session.dto.SessionResponse;
import com.squad.session.repository.SessionRepository;
import com.squad.squad.domain.Squad;
import com.squad.squad.repository.SquadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SquadRepository squadRepository;
    private final SecretRepository secretRepository;

    public SessionService(SessionRepository sessionRepository,
                          SquadRepository squadRepository,
                          SecretRepository secretRepository) {
        this.sessionRepository = sessionRepository;
        this.squadRepository = squadRepository;
        this.secretRepository = secretRepository;
    }

    public List<SessionResponse> findAll() {
        return sessionRepository.findAll().stream()
                .map(SessionResponse::from)
                .toList();
    }

    public SessionResponse findById(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SESSION_NOT_FOUND));
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse create(SessionCreateRequest request) {
        Squad squad = squadRepository.findById(request.squadId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.SQUAD_NOT_FOUND));

        Session.SessionBuilder builder = Session.builder()
                .squad(squad)
                .userPrompt(request.userPrompt())
                .status(SessionStatus.PENDING);

        if (request.repoUrl() != null && !request.repoUrl().isBlank()) {
            String repoUrl = request.repoUrl().trim();
            validateGitUrl(repoUrl);

            GitProvider provider = resolveGitProvider(repoUrl, request.gitProvider());
            String branch = (request.branch() != null && !request.branch().isBlank())
                    ? request.branch().trim() : "main";

            builder.repoUrl(repoUrl)
                    .branch(branch)
                    .gitProvider(provider);

            if (request.gitSecretName() != null && !request.gitSecretName().isBlank()) {
                String secretName = request.gitSecretName().trim();
                validateGitSecretExists(secretName);
                builder.gitSecretName(secretName);
            }
        }

        return SessionResponse.from(sessionRepository.save(builder.build()));
    }

    @Transactional
    public SessionResponse cancel(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new ValidationException(ErrorCode.INVALID_SESSION_STATE, "완료된 세션은 취소할 수 없습니다.");
        }

        session.cancel();
        return SessionResponse.from(session);
    }

    /**
     * Git 저장소 URL의 형식을 검증한다.
     *
     * @param repoUrl 검증할 URL
     * @throws ValidationException URL 형식이 올바르지 않은 경우
     */
    private void validateGitUrl(String repoUrl) {
        try {
            URI uri = URI.create(repoUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("https") && !scheme.equals("http"))) {
                throw new ValidationException(ErrorCode.INVALID_GIT_URL);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ValidationException(ErrorCode.INVALID_GIT_URL);
            }
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.INVALID_GIT_URL);
        }
    }

    /**
     * Git Provider를 자동 판별하거나 명시적 값을 검증한다.
     *
     * <p>URL에 {@code github.com}이 포함되면 GITHUB, {@code gitlab.com}이 포함되면 GITLAB로 판별한다.
     * 그 외(self-hosted)의 경우 {@code gitProvider} 파라미터가 필수이다.</p>
     *
     * @param repoUrl     저장소 URL
     * @param gitProvider 명시적 Provider 문자열 (nullable)
     * @return 판별된 GitProvider
     * @throws ValidationException Provider를 판별할 수 없는 경우
     */
    private GitProvider resolveGitProvider(String repoUrl, String gitProvider) {
        if (gitProvider != null && !gitProvider.isBlank()) {
            try {
                return GitProvider.valueOf(gitProvider.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException(ErrorCode.INVALID_GIT_PROVIDER);
            }
        }

        String host = URI.create(repoUrl).getHost().toLowerCase();
        if (host.contains("github.com")) {
            return GitProvider.GITHUB;
        }
        if (host.contains("gitlab.com")) {
            return GitProvider.GITLAB;
        }

        throw new ValidationException(ErrorCode.INVALID_GIT_PROVIDER);
    }

    /**
     * Git Secret 이름으로 Secret 존재 여부를 확인한다.
     *
     * @param secretName Secret 이름
     * @throws NotFoundException Secret이 존재하지 않는 경우
     */
    private void validateGitSecretExists(String secretName) {
        secretRepository.findByName(secretName)
                .orElseThrow(() -> new NotFoundException(ErrorCode.GIT_SECRET_NOT_FOUND));
    }
}
