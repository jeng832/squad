package com.squad.cli.shell;

import com.squad.cli.util.FuzzySearchEngine;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.Optional;

/**
 * 슬래시 커맨드 자동완성 Completer.
 *
 * <p>{@code /} 접두사 입력 시 등록된 커맨드를 퍼지 검색으로 필터링하여
 * 후보 목록을 제공한다. 각 후보에는 커맨드 설명이 함께 표시된다.</p>
 *
 * <p>화살표 키 탐색 시 {@code >} 접두사로 선택 상태를 표시하며,
 * 후보 수에 관계없이 일관된 선택 UX를 제공한다.</p>
 */
public class SlashCommandCompleter implements Completer {

    private static final String SELECTED_PREFIX = "> ";
    private static final String UNSELECTED_PREFIX = "  ";

    private final CommandRegistry commandRegistry;
    private final FuzzySearchEngine fuzzySearchEngine;

    private int selectedIndex = -1;
    private String anchorQuery = "";
    private List<String> lastMatchedNames = List.of();

    public SlashCommandCompleter(CommandRegistry commandRegistry, FuzzySearchEngine fuzzySearchEngine) {
        this.commandRegistry = commandRegistry;
        this.fuzzySearchEngine = fuzzySearchEngine;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.word();

        if (!buffer.startsWith("/") && !line.line().isEmpty()) {
            return;
        }

        String query = buffer.startsWith("/") ? buffer.substring(1) : "";

        if (selectedIndex >= 0 && !query.equals(anchorQuery)) {
            resetSelection();
        }

        List<String> matched;
        if (selectedIndex >= 0) {
            matched = lastMatchedNames;
        } else {
            List<String> allNames = commandRegistry.getAll().keySet().stream()
                    .sorted()
                    .toList();
            matched = query.isEmpty()
                    ? allNames
                    : fuzzySearchEngine.search(query, allNames).stream()
                            .sorted()
                            .toList();
        }

        int maxNameLen = commandRegistry.getAll().keySet().stream()
                .mapToInt(String::length)
                .max()
                .orElse(0);

        int termWidth = reader.getTerminal().getWidth();
        int prefixLen = SELECTED_PREFIX.length();
        int minDisplayWidth = (termWidth / 2) + 1;

        for (int i = 0; i < matched.size(); i++) {
            String name = matched.get(i);
            int idx = i;
            commandRegistry.find(name).ifPresent(entry -> {
                String prefix = (selectedIndex >= 0 && idx == selectedIndex)
                        ? SELECTED_PREFIX
                        : UNSELECTED_PREFIX;
                String padded = entry.name() + " ".repeat(maxNameLen - entry.name().length());
                String content = prefix + padded + "  " + entry.description();
                int currentWidth = displayWidth(content);
                String display = currentWidth >= minDisplayWidth
                        ? content
                        : content + " ".repeat(minDisplayWidth - currentWidth);
                candidates.add(new Candidate(
                        "/" + entry.name(),
                        display,
                        null,
                        null,
                        null,
                        null,
                        true
                ));
            });
        }
    }

    /**
     * 탐색 모드를 시작한다.
     *
     * <p>현재 쿼리와 매칭된 커맨드 목록을 캐시하고,
     * 첫 번째 항목을 선택 상태로 설정한다.</p>
     *
     * @param query        사용자가 입력한 쿼리 (앵커)
     * @param matchedNames 매칭된 커맨드 이름 목록
     */
    public void startNavigation(String query, List<String> matchedNames) {
        this.anchorQuery = query;
        this.lastMatchedNames = List.copyOf(matchedNames);
        this.selectedIndex = 0;
    }

    /**
     * 다음 항목을 선택한다. 마지막 항목에서 첫 항목으로 순환한다.
     */
    public void selectNext() {
        if (lastMatchedNames.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex + 1) % lastMatchedNames.size();
    }

    /**
     * 이전 항목을 선택한다. 첫 항목에서 마지막 항목으로 순환한다.
     */
    public void selectPrevious() {
        if (lastMatchedNames.isEmpty()) {
            return;
        }
        selectedIndex = selectedIndex <= 0
                ? lastMatchedNames.size() - 1
                : selectedIndex - 1;
    }

    /**
     * 선택 상태를 초기화한다.
     */
    public void resetSelection() {
        selectedIndex = -1;
        anchorQuery = "";
        lastMatchedNames = List.of();
    }

    /**
     * 현재 탐색 모드 여부를 반환한다.
     *
     * @return 탐색 중이면 {@code true}
     */
    public boolean isNavigating() {
        return selectedIndex >= 0;
    }

    /**
     * 탐색 시작 시 캐시된 앵커 쿼리를 반환한다.
     *
     * @return 앵커 쿼리 문자열
     */
    public String getAnchorQuery() {
        return anchorQuery;
    }

    /**
     * 현재 선택된 커맨드 이름을 반환한다.
     *
     * @return 선택된 이름, 탐색 중이 아니면 빈 Optional
     */
    public Optional<String> getSelectedName() {
        if (selectedIndex >= 0 && selectedIndex < lastMatchedNames.size()) {
            return Optional.of(lastMatchedNames.get(selectedIndex));
        }
        return Optional.empty();
    }

    private int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            width += isWideCharacter(codePoint) ? 2 : 1;
            i += Character.charCount(codePoint);
        }
        return width;
    }

    private boolean isWideCharacter(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2E80 && codePoint <= 0x9FFF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE10 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF01 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x20000 && codePoint <= 0x2FFFF);
    }
}
