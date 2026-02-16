package com.squad.cli.shell;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 슬래시 커맨드를 등록하고 조회하는 레지스트리.
 *
 * <p>커맨드 이름(슬래시 제외)을 키로, 설명과 실행기를 값으로 관리한다.
 * 등록 순서를 유지하여 목록 출력 시 일관된 순서를 보장한다.</p>
 */
@Component
public class CommandRegistry {

    private final Map<String, CommandEntry> commands = new LinkedHashMap<>();

    /**
     * 커맨드를 등록한다.
     *
     * @param name        커맨드 이름 (슬래시 제외, 예: "help")
     * @param description 커맨드 설명
     * @param executor    커맨드 실행기
     * @throws IllegalArgumentException name이 null이거나 빈 문자열인 경우
     */
    public void register(String name, String description, CommandExecutor executor) {
        register(name, description, executor, List.of());
    }

    /**
     * 서브커맨드를 포함하여 커맨드를 등록한다.
     *
     * @param name        커맨드 이름 (슬래시 제외, 예: "agent")
     * @param description 커맨드 설명
     * @param executor    커맨드 실행기
     * @param subcommands 서브커맨드 목록
     * @throws IllegalArgumentException name이 null이거나 빈 문자열인 경우
     */
    public void register(String name, String description, CommandExecutor executor,
                          List<SubcommandInfo> subcommands) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("커맨드 이름은 비어있을 수 없습니다.");
        }
        if (executor == null) {
            throw new IllegalArgumentException("커맨드 실행기는 null일 수 없습니다.");
        }
        List<SubcommandInfo> subs = subcommands != null ? List.copyOf(subcommands) : List.of();
        commands.put(name.toLowerCase(), new CommandEntry(name.toLowerCase(), description, executor, subs));
    }

    /**
     * 이름으로 커맨드를 조회한다.
     *
     * @param name 커맨드 이름 (슬래시 제외)
     * @return 커맨드 엔트리 (없으면 empty)
     */
    public Optional<CommandEntry> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(commands.get(name.toLowerCase()));
    }

    /**
     * 등록된 모든 커맨드를 반환한다.
     *
     * @return 등록 순서를 유지하는 불변 맵
     */
    public Map<String, CommandEntry> getAll() {
        return Collections.unmodifiableMap(commands);
    }

    /**
     * 등록된 커맨드 수를 반환한다.
     *
     * @return 커맨드 수
     */
    public int size() {
        return commands.size();
    }

    /**
     * 서브커맨드 정보.
     *
     * @param name        서브커맨드 이름
     * @param description 서브커맨드 설명
     */
    public record SubcommandInfo(String name, String description) {
    }

    /**
     * 커맨드 엔트리.
     *
     * @param name        커맨드 이름
     * @param description 커맨드 설명
     * @param executor    커맨드 실행기
     * @param subcommands 서브커맨드 목록
     */
    public record CommandEntry(String name, String description, CommandExecutor executor,
                                List<SubcommandInfo> subcommands) {

        /**
         * 서브커맨드가 있는지 여부를 반환한다.
         *
         * @return 서브커맨드가 있으면 {@code true}
         */
        public boolean hasSubcommands() {
            return subcommands != null && !subcommands.isEmpty();
        }
    }

    /**
     * 커맨드 실행기 함수형 인터페이스.
     */
    @FunctionalInterface
    public interface CommandExecutor {

        /**
         * 커맨드를 실행한다.
         *
         * @param ctx  커맨드 실행 컨텍스트 (터미널, LineReader 등)
         * @param args 커맨드 인자 (슬래시와 커맨드 이름 제외)
         */
        void execute(CommandContext ctx, String args);
    }
}
