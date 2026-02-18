package com.squad.session.domain;

import java.net.URI;

/**
 * Git Provider별 인증 URL을 조립하는 유틸리티.
 *
 * <p>Personal Access Token(PAT)을 포함한 Clone URL을 생성한다.
 * GitHub와 GitLab(self-hosted 포함)의 인증 방식 차이를 캡슐화한다.</p>
 *
 * <ul>
 *   <li>GITHUB: {@code https://{token}@{host}/{path}.git}</li>
 *   <li>GITLAB: {@code https://oauth2:{token}@{host}/{path}.git}</li>
 * </ul>
 */
public final class GitCloneUrlBuilder {

    private GitCloneUrlBuilder() {
    }

    /**
     * Provider별 인증이 포함된 Git Clone URL을 생성한다.
     *
     * <p>token이 null이거나 빈 문자열이면 원본 URL을 그대로 반환한다 (public repo 용).</p>
     *
     * @param repoUrl  원본 저장소 URL
     * @param provider Git Provider
     * @param token    Personal Access Token (nullable)
     * @return 인증이 포함된 Clone URL
     */
    public static String build(String repoUrl, GitProvider provider, String token) {
        if (token == null || token.isBlank()) {
            return ensureGitSuffix(repoUrl);
        }

        URI uri = URI.create(repoUrl);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        String path = uri.getPath();

        String hostPort = port > 0 ? host + ":" + port : host;
        String gitPath = ensureGitSuffix(path);

        return switch (provider) {
            case GITHUB -> scheme + "://" + token + "@" + hostPort + gitPath;
            case GITLAB -> scheme + "://oauth2:" + token + "@" + hostPort + gitPath;
        };
    }

    private static String ensureGitSuffix(String value) {
        if (value.endsWith(".git")) {
            return value;
        }
        return value + ".git";
    }
}
