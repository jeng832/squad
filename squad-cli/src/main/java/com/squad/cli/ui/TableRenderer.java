package com.squad.cli.ui;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 테이블 포맷 출력 유틸리티.
 *
 * <p>데이터를 정렬된 텍스트 테이블로 렌더링한다.
 * 각 열 너비는 데이터에 맞게 자동으로 조정된다.</p>
 *
 * <pre>
 * 사용 예:
 * TableRenderer renderer = new TableRenderer();
 * String table = renderer.builder()
 *     .headers("ID", "이름", "상태")
 *     .row("1", "에이전트-A", "활성")
 *     .row("2", "에이전트-B", "비활성")
 *     .build();
 * System.out.println(table);
 * </pre>
 */
@Component
public class TableRenderer {

    /**
     * 테이블 빌더를 생성한다.
     *
     * @return 새로운 빌더 인스턴스
     */
    public Builder builder() {
        return new Builder();
    }

    /**
     * 테이블 빌더.
     *
     * <p>헤더와 행 데이터를 추가한 뒤 {@link #build()}로 포맷된 문자열을 생성한다.</p>
     */
    public static class Builder {

        private String[] headers;
        private final List<String[]> rows = new ArrayList<>();

        private Builder() {
        }

        /**
         * 헤더를 설정한다.
         *
         * @param headers 열 헤더 배열
         * @return 빌더 인스턴스
         */
        public Builder headers(String... headers) {
            this.headers = headers;
            return this;
        }

        /**
         * 행을 추가한다.
         *
         * @param values 열 값 배열
         * @return 빌더 인스턴스
         */
        public Builder row(String... values) {
            rows.add(values);
            return this;
        }

        /**
         * 테이블 문자열을 생성한다.
         *
         * @return 포맷된 테이블 문자열
         * @throws IllegalStateException 헤더가 설정되지 않은 경우
         */
        public String build() {
            if (headers == null || headers.length == 0) {
                throw new IllegalStateException("헤더가 설정되지 않았습니다.");
            }

            int columnCount = headers.length;
            int[] widths = calculateColumnWidths(columnCount);

            StringBuilder sb = new StringBuilder();
            appendRow(sb, headers, widths);
            appendSeparator(sb, widths);

            for (String[] row : rows) {
                appendRow(sb, row, widths);
            }

            return sb.toString().stripTrailing();
        }

        private int[] calculateColumnWidths(int columnCount) {
            int[] widths = new int[columnCount];

            for (int i = 0; i < columnCount; i++) {
                widths[i] = displayWidth(headers[i]);
            }

            for (String[] row : rows) {
                for (int i = 0; i < Math.min(row.length, columnCount); i++) {
                    widths[i] = Math.max(widths[i], displayWidth(row[i]));
                }
            }

            return widths;
        }

        private void appendRow(StringBuilder sb, String[] values, int[] widths) {
            for (int i = 0; i < widths.length; i++) {
                String value = (i < values.length && values[i] != null) ? values[i] : "";
                int padding = widths[i] - displayWidth(value);
                sb.append(value);
                sb.append(" ".repeat(Math.max(0, padding)));
                if (i < widths.length - 1) {
                    sb.append("  ");
                }
            }
            sb.append('\n');
        }

        private void appendSeparator(StringBuilder sb, int[] widths) {
            for (int i = 0; i < widths.length; i++) {
                sb.append("-".repeat(widths[i]));
                if (i < widths.length - 1) {
                    sb.append("  ");
                }
            }
            sb.append('\n');
        }

        private int displayWidth(String text) {
            if (text == null) {
                return 0;
            }
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
                    || (codePoint >= 0x20000 && codePoint <= 0x2FFFF)
                    || (codePoint >= 0x30000 && codePoint <= 0x3FFFF);
        }
    }
}
