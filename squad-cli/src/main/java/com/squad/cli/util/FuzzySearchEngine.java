package com.squad.cli.util;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 퍼지 검색 엔진.
 *
 * <p>부분 문자열 매칭과 레벤슈타인 거리를 결합하여 유사도 점수를 산출한다.
 * 대소문자를 무시하며, 연속 매칭에 가점을 부여한다.</p>
 */
@Component
public class FuzzySearchEngine {

    private static final int MAX_RESULTS = 10;

    /**
     * 쿼리와 유사한 항목을 스코어 내림차순으로 반환한다.
     *
     * @param query      검색 쿼리
     * @param candidates 후보 문자열 목록
     * @return 매칭된 항목 목록 (최대 {@value MAX_RESULTS}개)
     */
    public List<String> search(String query, List<String> candidates) {
        if (query == null || query.isBlank()) {
            return candidates.stream()
                    .limit(MAX_RESULTS)
                    .toList();
        }

        String lowerQuery = query.toLowerCase();

        return candidates.stream()
                .map(candidate -> new ScoredItem(candidate, calculateScore(lowerQuery, candidate.toLowerCase())))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredItem::score).reversed())
                .limit(MAX_RESULTS)
                .map(ScoredItem::value)
                .toList();
    }

    /**
     * 유사도 점수를 계산한다.
     *
     * <p>점수 산출 기준:
     * <ul>
     *   <li>완전 일치: 1000점</li>
     *   <li>접두사 매칭: 500점</li>
     *   <li>부분 문자열 매칭: 300점</li>
     *   <li>연속 문자 매칭 가점: 연속 길이 * 50점</li>
     *   <li>레벤슈타인 거리 기반 점수: (1 - distance/maxLen) * 200</li>
     * </ul>
     * </p>
     *
     * @param query     검색 쿼리 (소문자)
     * @param candidate 후보 문자열 (소문자)
     * @return 유사도 점수 (0 이하면 매칭 실패)
     */
    int calculateScore(String query, String candidate) {
        if (candidate.equals(query)) {
            return 1000;
        }

        int score = 0;

        if (candidate.startsWith(query)) {
            score += 500;
        } else if (candidate.contains(query)) {
            score += 300;
        }

        score += calculateConsecutiveMatchBonus(query, candidate);

        int levenshteinScore = calculateLevenshteinScore(query, candidate);
        score += levenshteinScore;

        return score;
    }

    private int calculateConsecutiveMatchBonus(String query, String candidate) {
        int maxConsecutive = 0;
        int currentConsecutive = 0;
        int candidateIndex = 0;

        for (int i = 0; i < query.length(); i++) {
            char queryChar = query.charAt(i);
            boolean found = false;

            while (candidateIndex < candidate.length()) {
                if (candidate.charAt(candidateIndex) == queryChar) {
                    currentConsecutive++;
                    candidateIndex++;
                    found = true;
                    break;
                }
                currentConsecutive = 0;
                candidateIndex++;
            }

            if (!found) {
                break;
            }

            maxConsecutive = Math.max(maxConsecutive, currentConsecutive);
        }

        return maxConsecutive * 50;
    }

    /**
     * 레벤슈타인 거리를 계산하고 점수로 변환한다.
     *
     * @param source 원본 문자열
     * @param target 대상 문자열
     * @return 레벤슈타인 기반 점수 (0~200)
     */
    int calculateLevenshteinScore(String source, String target) {
        int distance = levenshteinDistance(source, target);
        int maxLen = Math.max(source.length(), target.length());
        if (maxLen == 0) {
            return 200;
        }
        double similarity = 1.0 - (double) distance / maxLen;
        return Math.max(0, (int) (similarity * 200));
    }

    /**
     * 두 문자열 간의 레벤슈타인 거리를 계산한다.
     *
     * @param source 원본 문자열
     * @param target 대상 문자열
     * @return 편집 거리
     */
    int levenshteinDistance(String source, String target) {
        int sourceLen = source.length();
        int targetLen = target.length();

        int[] previousRow = new int[targetLen + 1];
        int[] currentRow = new int[targetLen + 1];

        for (int j = 0; j <= targetLen; j++) {
            previousRow[j] = j;
        }

        for (int i = 1; i <= sourceLen; i++) {
            currentRow[0] = i;
            for (int j = 1; j <= targetLen; j++) {
                int cost = (source.charAt(i - 1) == target.charAt(j - 1)) ? 0 : 1;
                currentRow[j] = Math.min(
                        Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                        previousRow[j - 1] + cost
                );
            }
            int[] temp = previousRow;
            previousRow = currentRow;
            currentRow = temp;
        }

        return previousRow[targetLen];
    }

    private record ScoredItem(String value, int score) {
    }
}
