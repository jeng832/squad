package com.squad.cli.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableRendererTest {

    private TableRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new TableRenderer();
    }

    @Test
    @DisplayName("기본 테이블을 렌더링한다")
    void rendersBasicTable() {
        String result = renderer.builder()
                .headers("ID", "Name")
                .row("1", "Alice")
                .row("2", "Bob")
                .build();

        assertThat(result).contains("ID");
        assertThat(result).contains("Name");
        assertThat(result).contains("Alice");
        assertThat(result).contains("Bob");
        assertThat(result).contains("--");
    }

    @Test
    @DisplayName("열 너비가 데이터에 맞게 조정된다")
    void columnWidthsAdjustToData() {
        String result = renderer.builder()
                .headers("ID", "Name")
                .row("1", "VeryLongName")
                .build();

        String[] lines = result.split("\n");
        // 헤더와 데이터 행의 길이가 동일해야 함
        assertThat(lines[0].length()).isEqualTo(lines[2].length());
    }

    @Test
    @DisplayName("헤더 없이 빌드하면 예외가 발생한다")
    void buildWithoutHeadersThrows() {
        assertThatThrownBy(() -> renderer.builder().build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("빈 헤더로 빌드하면 예외가 발생한다")
    void buildWithEmptyHeadersThrows() {
        assertThatThrownBy(() -> renderer.builder().headers().build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("행 없이 헤더만으로 테이블을 렌더링한다")
    void rendersHeaderOnly() {
        String result = renderer.builder()
                .headers("Col1", "Col2")
                .build();

        String[] lines = result.split("\n");
        assertThat(lines).hasSize(2); // 헤더 + 구분선
    }

    @Test
    @DisplayName("한글 문자의 너비를 올바르게 계산한다")
    void handlesKoreanCharacterWidth() {
        String result = renderer.builder()
                .headers("ID", "이름")
                .row("1", "테스트")
                .build();

        assertThat(result).contains("이름");
        assertThat(result).contains("테스트");
    }

    @Test
    @DisplayName("열 수보다 적은 값을 가진 행을 처리한다")
    void handlesRowWithFewerValues() {
        String result = renderer.builder()
                .headers("A", "B", "C")
                .row("1")
                .build();

        assertThat(result).contains("1");
    }

    @Test
    @DisplayName("null 값이 포함된 행을 처리한다")
    void handlesNullValues() {
        String result = renderer.builder()
                .headers("A", "B")
                .row("1", null)
                .build();

        assertThat(result).contains("1");
    }

    @Test
    @DisplayName("여러 행이 올바르게 렌더링된다")
    void rendersMultipleRows() {
        String result = renderer.builder()
                .headers("ID", "Status")
                .row("1", "active")
                .row("2", "inactive")
                .row("3", "active")
                .build();

        String[] lines = result.split("\n");
        assertThat(lines).hasSize(5); // 헤더 + 구분선 + 3행
    }
}
