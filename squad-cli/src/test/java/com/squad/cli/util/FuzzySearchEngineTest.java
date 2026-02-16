package com.squad.cli.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuzzySearchEngineTest {

    private FuzzySearchEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FuzzySearchEngine();
    }

    @Test
    @DisplayName("완전 일치 항목이 최상위에 위치한다")
    void exactMatchRanksFirst() {
        List<String> candidates = List.of("helper", "help", "helping");

        List<String> result = engine.search("help", candidates);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).isEqualTo("help");
    }

    @Test
    @DisplayName("접두사 매칭이 부분 매칭보다 높은 순위를 갖는다")
    void prefixMatchRanksHigherThanSubstring() {
        List<String> candidates = List.of("xhelp", "helpme");

        List<String> result = engine.search("help", candidates);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).isEqualTo("helpme");
    }

    @Test
    @DisplayName("빈 쿼리는 전체 후보 목록을 반환한다")
    void emptyQueryReturnsAll() {
        List<String> candidates = List.of("a", "b", "c");

        List<String> result = engine.search("", candidates);

        assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("null 쿼리는 전체 후보 목록을 반환한다")
    void nullQueryReturnsAll() {
        List<String> candidates = List.of("a", "b");

        List<String> result = engine.search(null, candidates);

        assertThat(result).containsExactly("a", "b");
    }

    @Test
    @DisplayName("매칭 결과가 없으면 빈 리스트를 반환한다")
    void noMatchReturnsEmpty() {
        List<String> candidates = List.of("abc", "def");

        List<String> result = engine.search("xyz", candidates);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("대소문자를 무시하여 매칭한다")
    void searchIsCaseInsensitive() {
        List<String> candidates = List.of("Help", "QUIT", "status");

        List<String> result = engine.search("help", candidates);

        assertThat(result).contains("Help");
    }

    @Test
    @DisplayName("결과 수는 최대 10개로 제한된다")
    void resultsAreLimitedToMax() {
        List<String> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candidates.add("item" + i);
        }

        List<String> result = engine.search("item", candidates);

        assertThat(result).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("레벤슈타인 거리가 올바르게 계산된다")
    void levenshteinDistanceIsCorrect() {
        assertThat(engine.levenshteinDistance("", "")).isZero();
        assertThat(engine.levenshteinDistance("abc", "abc")).isZero();
        assertThat(engine.levenshteinDistance("abc", "")).isEqualTo(3);
        assertThat(engine.levenshteinDistance("", "abc")).isEqualTo(3);
        assertThat(engine.levenshteinDistance("kitten", "sitting")).isEqualTo(3);
    }

    @Test
    @DisplayName("연속 매칭이 더 높은 점수를 받는다")
    void consecutiveMatchGetsHigherScore() {
        int scoreConsecutive = engine.calculateScore("hel", "help");
        int scoreScattered = engine.calculateScore("hel", "hotel");

        assertThat(scoreConsecutive).isGreaterThan(scoreScattered);
    }

    @Test
    @DisplayName("빈 후보 목록에서 검색 시 빈 결과를 반환한다")
    void searchWithEmptyCandidates() {
        List<String> result = engine.search("test", List.of());

        assertThat(result).isEmpty();
    }
}
