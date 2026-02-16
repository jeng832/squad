package com.squad.cli.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * CLI 전용 설정 빈.
 *
 * <p>서버 URL, 히스토리 파일 경로 등 CLI 동작에 필요한 설정 값을 관리한다.</p>
 */
@Configuration
public class CliConfig {

    private final String serverUrl;
    private final String historyFilePath;
    private final String prompt;

    public CliConfig(
            @Value("${squad.cli.server-url:http://localhost:8080}") String serverUrl,
            @Value("${squad.cli.history-file:#{systemProperties['user.home'] + '/.squad/history'}}") String historyFilePath,
            @Value("${squad.cli.prompt:squad> }") String prompt) {
        this.serverUrl = serverUrl;
        this.historyFilePath = historyFilePath;
        this.prompt = prompt;
    }

    /**
     * Squad 서버 URL을 반환한다.
     *
     * @return 서버 URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * 히스토리 파일 경로를 반환한다.
     *
     * @return 히스토리 파일 절대 경로
     */
    public String getHistoryFilePath() {
        return historyFilePath;
    }

    /**
     * REPL 프롬프트 문자열을 반환한다.
     *
     * @return 프롬프트 문자열
     */
    public String getPrompt() {
        return prompt;
    }
}
