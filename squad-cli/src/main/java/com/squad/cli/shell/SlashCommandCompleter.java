package com.squad.cli.shell;

import com.squad.cli.util.FuzzySearchEngine;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

/**
 * 슬래시 커맨드 자동완성 Completer.
 *
 * <p>{@code /} 접두사 입력 시 등록된 커맨드를 퍼지 검색으로 필터링하여
 * 후보 목록을 제공한다. 각 후보에는 커맨드 설명이 함께 표시된다.</p>
 */
public class SlashCommandCompleter implements Completer {

    private final CommandRegistry commandRegistry;
    private final FuzzySearchEngine fuzzySearchEngine;

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

        List<String> allNames = commandRegistry.getAll().keySet().stream()
                .toList();

        List<String> matched = query.isEmpty()
                ? allNames
                : fuzzySearchEngine.search(query, allNames);

        int maxNameLen = matched.stream()
                .mapToInt(String::length)
                .max()
                .orElse(0);

        int termWidth = reader.getTerminal().getWidth();
        int minDisplayWidth = (termWidth / 2) + 1;

        for (String name : matched) {
            commandRegistry.find(name).ifPresent(entry -> {
                String padded = entry.name() + " ".repeat(maxNameLen - entry.name().length());
                String content = padded + "  " + entry.description();
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
