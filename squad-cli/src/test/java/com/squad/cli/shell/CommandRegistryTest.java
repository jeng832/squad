package com.squad.cli.shell;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class CommandRegistryTest {

    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
    }

    @Test
    @DisplayName("커맨드를 등록하고 이름으로 조회할 수 있다")
    void registerAndFind() {
        registry.register("help", "도움말 표시", (ctx, args) -> {});

        Optional<CommandRegistry.CommandEntry> result = registry.find("help");

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("help");
        assertThat(result.get().description()).isEqualTo("도움말 표시");
    }

    @Test
    @DisplayName("대소문자를 무시하고 커맨드를 조회한다")
    void findIgnoresCase() {
        registry.register("Help", "도움말 표시", (ctx, args) -> {});

        assertThat(registry.find("help")).isPresent();
        assertThat(registry.find("HELP")).isPresent();
        assertThat(registry.find("Help")).isPresent();
    }

    @Test
    @DisplayName("등록되지 않은 커맨드 조회 시 empty를 반환한다")
    void findUnregisteredReturnsEmpty() {
        assertThat(registry.find("unknown")).isEmpty();
    }

    @Test
    @DisplayName("null 이름으로 조회 시 empty를 반환한다")
    void findNullReturnsEmpty() {
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    @DisplayName("null 이름으로 등록 시 예외가 발생한다")
    void registerNullNameThrows() {
        assertThatThrownBy(() -> registry.register(null, "설명", (ctx, args) -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 이름으로 등록 시 예외가 발생한다")
    void registerBlankNameThrows() {
        assertThatThrownBy(() -> registry.register("  ", "설명", (ctx, args) -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 실행기로 등록 시 예외가 발생한다")
    void registerNullExecutorThrows() {
        assertThatThrownBy(() -> registry.register("test", "설명", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("등록된 모든 커맨드를 조회할 수 있다")
    void getAllReturnsAllCommands() {
        registry.register("help", "도움말", (ctx, args) -> {});
        registry.register("quit", "종료", (ctx, args) -> {});

        Map<String, CommandRegistry.CommandEntry> all = registry.getAll();

        assertThat(all).hasSize(2);
        assertThat(all).containsKeys("help", "quit");
    }

    @Test
    @DisplayName("getAll()은 등록 순서를 유지한다")
    void getAllPreservesInsertionOrder() {
        registry.register("alpha", "첫 번째", (ctx, args) -> {});
        registry.register("beta", "두 번째", (ctx, args) -> {});
        registry.register("gamma", "세 번째", (ctx, args) -> {});

        assertThat(registry.getAll().keySet())
                .containsExactly("alpha", "beta", "gamma");
    }

    @Test
    @DisplayName("getAll()은 불변 맵을 반환한다")
    void getAllReturnsUnmodifiableMap() {
        registry.register("help", "도움말", (ctx, args) -> {});

        Map<String, CommandRegistry.CommandEntry> all = registry.getAll();

        assertThatThrownBy(() -> all.put("new", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("size()는 등록된 커맨드 수를 반환한다")
    void sizeReturnsCorrectCount() {
        assertThat(registry.size()).isZero();

        registry.register("help", "도움말", (ctx, args) -> {});
        assertThat(registry.size()).isEqualTo(1);

        registry.register("quit", "종료", (ctx, args) -> {});
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 이름으로 재등록하면 덮어쓴다")
    void registerOverwritesExisting() {
        registry.register("help", "이전 설명", (ctx, args) -> {});
        registry.register("help", "새 설명", (ctx, args) -> {});

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("help").get().description()).isEqualTo("새 설명");
    }

    @Test
    @DisplayName("커맨드 실행기가 정상적으로 호출된다")
    void executorIsInvoked() {
        StringBuilder captured = new StringBuilder();
        registry.register("echo", "에코", (ctx, args) -> captured.append(args));

        registry.find("echo").get().executor().execute(null, "hello");

        assertThat(captured).hasToString("hello");
    }
}
