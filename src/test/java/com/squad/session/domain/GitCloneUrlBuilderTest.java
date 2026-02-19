package com.squad.session.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GitCloneUrlBuilder 단위 테스트")
class GitCloneUrlBuilderTest {

    @Test
    @DisplayName("GITHUB 토큰이 포함된 URL을 생성한다")
    void buildGitHubWithToken() {
        String result = GitCloneUrlBuilder.build(
                "https://github.com/owner/repo", GitProvider.GITHUB, "ghp_abc123");

        assertThat(result).isEqualTo("https://ghp_abc123@github.com/owner/repo.git");
    }

    @Test
    @DisplayName("GITLAB 토큰이 포함된 URL을 생성한다")
    void buildGitLabWithToken() {
        String result = GitCloneUrlBuilder.build(
                "https://gitlab.com/owner/repo", GitProvider.GITLAB, "glpat-xyz789");

        assertThat(result).isEqualTo("https://oauth2:glpat-xyz789@gitlab.com/owner/repo.git");
    }

    @Test
    @DisplayName("토큰이 null이면 원본 URL에 .git suffix를 붙여 반환한다")
    void buildWithNullToken() {
        String result = GitCloneUrlBuilder.build(
                "https://github.com/owner/repo", GitProvider.GITHUB, null);

        assertThat(result).isEqualTo("https://github.com/owner/repo.git");
    }

    @Test
    @DisplayName("토큰이 빈 문자열이면 원본 URL에 .git suffix를 붙여 반환한다")
    void buildWithBlankToken() {
        String result = GitCloneUrlBuilder.build(
                "https://github.com/owner/repo", GitProvider.GITHUB, "  ");

        assertThat(result).isEqualTo("https://github.com/owner/repo.git");
    }

    @Test
    @DisplayName("이미 .git suffix가 있는 URL은 중복 추가하지 않는다")
    void buildWithExistingGitSuffix() {
        String result = GitCloneUrlBuilder.build(
                "https://github.com/owner/repo.git", GitProvider.GITHUB, "token");

        assertThat(result).isEqualTo("https://token@github.com/owner/repo.git");
    }

    @Test
    @DisplayName("self-hosted GitLab URL도 정상 처리한다")
    void buildSelfHostedGitLab() {
        String result = GitCloneUrlBuilder.build(
                "https://git.mycompany.com/team/project", GitProvider.GITLAB, "my-token");

        assertThat(result).isEqualTo("https://oauth2:my-token@git.mycompany.com/team/project.git");
    }

    @Test
    @DisplayName("포트가 포함된 URL도 정상 처리한다")
    void buildWithPort() {
        String result = GitCloneUrlBuilder.build(
                "https://git.mycompany.com:8443/team/project", GitProvider.GITHUB, "token");

        assertThat(result).isEqualTo("https://token@git.mycompany.com:8443/team/project.git");
    }
}
