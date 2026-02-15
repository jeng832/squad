package com.squad.cli.shell;

import com.squad.cli.util.FuzzySearchEngine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 슬래시 커맨드 팔레트.
 *
 * <p>퍼지 검색으로 커맨드를 필터링하고, 목록을 포맷하여 제공한다.
 * 인터랙티브 셸에서 {@code /} 입력 시 자동완성과 커맨드 선택에 사용된다.</p>
 */
@Component
public class SlashCommandPalette {

    private final CommandRegistry commandRegistry;
    private final FuzzySearchEngine fuzzySearchEngine;

    public SlashCommandPalette(CommandRegistry commandRegistry, FuzzySearchEngine fuzzySearchEngine) {
        this.commandRegistry = commandRegistry;
        this.fuzzySearchEngine = fuzzySearchEngine;
    }

    /**
     * 쿼리에 매칭되는 커맨드 목록을 반환한다.
     *
     * <p>빈 쿼리이면 전체 목록을, 그렇지 않으면 퍼지 매칭 결과를 반환한다.</p>
     *
     * @param query 검색 쿼리 (슬래시 제외)
     * @return 매칭된 커맨드 이름 목록
     */
    public List<String> search(String query) {
        List<String> allNames = commandRegistry.getAll().keySet().stream()
                .toList();

        if (query == null || query.isBlank()) {
            return allNames;
        }

        return fuzzySearchEngine.search(query, allNames);
    }

    /**
     * 커맨드 팔레트 목록을 포맷된 문자열로 반환한다.
     *
     * @param query 검색 쿼리 (null이면 전체 목록)
     * @return 포맷된 커맨드 목록 문자열
     */
    public String formatPalette(String query) {
        List<String> matched = search(query);
        if (matched.isEmpty()) {
            return "일치하는 커맨드가 없습니다.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("사용 가능한 커맨드:\n");
        for (String name : matched) {
            commandRegistry.find(name).ifPresent(entry ->
                    sb.append(String.format("  /%s - %s%n", entry.name(), entry.description()))
            );
        }
        return sb.toString().stripTrailing();
    }
}
