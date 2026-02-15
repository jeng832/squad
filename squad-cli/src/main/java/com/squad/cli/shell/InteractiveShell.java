package com.squad.cli.shell;

import com.squad.cli.config.CliConfig;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JLine3 기반 인터랙티브 REPL 셸.
 *
 * <p>프롬프트를 표시하고 사용자 입력을 받아 슬래시 커맨드를 실행한다.
 * Tab 자동완성, 히스토리, 퍼지 커맨드 팔레트를 지원한다.</p>
 */
@Component
public class InteractiveShell {

    private final CliConfig cliConfig;
    private final CommandRegistry commandRegistry;
    private final SlashCommandPalette commandPalette;

    public InteractiveShell(CliConfig cliConfig,
                            CommandRegistry commandRegistry,
                            SlashCommandPalette commandPalette) {
        this.cliConfig = cliConfig;
        this.commandRegistry = commandRegistry;
        this.commandPalette = commandPalette;
        registerBuiltinCommands();
    }

    /**
     * REPL 루프를 시작한다.
     *
     * <p>Ctrl+C(UserInterrupt)는 현재 입력을 취소하고,
     * Ctrl+D(EndOfFile)는 셸을 종료한다.</p>
     *
     * @throws IOException 터미널 초기화 실패 시
     */
    public void start() throws IOException {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build()) {

            ensureHistoryDirectory();

            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new StringsCompleter(
                            commandRegistry.getAll().keySet().stream()
                                    .map(name -> "/" + name)
                                    .toList()
                    ))
                    .variable(LineReader.HISTORY_FILE, cliConfig.getHistoryFilePath())
                    .build();

            PrintWriter writer = terminal.writer();
            writer.println("Squad CLI v0.1.0 - '/help'로 사용 가능한 커맨드를 확인하세요.");
            writer.println("종료: Ctrl+D 또는 /quit");
            writer.flush();

            runLoop(lineReader, writer);
        }
    }

    private void runLoop(LineReader lineReader, PrintWriter writer) {
        while (true) {
            try {
                String line = lineReader.readLine(cliConfig.getPrompt()).trim();
                if (line.isEmpty()) {
                    continue;
                }
                processInput(line, writer);
            } catch (UserInterruptException e) {
                // Ctrl+C: 현재 입력 취소, 루프 계속
            } catch (EndOfFileException e) {
                writer.println("안녕히 가세요!");
                writer.flush();
                return;
            }
        }
    }

    private void processInput(String line, PrintWriter writer) {
        if (line.startsWith("/")) {
            executeSlashCommand(line, writer);
        } else {
            writer.println("알 수 없는 입력입니다. '/help'로 사용 가능한 커맨드를 확인하세요.");
            writer.flush();
        }
    }

    private void executeSlashCommand(String line, PrintWriter writer) {
        String withoutSlash = line.substring(1);
        String[] parts = withoutSlash.split("\\s+", 2);
        String commandName = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        commandRegistry.find(commandName)
                .ifPresentOrElse(
                        entry -> entry.executor().execute(args),
                        () -> {
                            String palette = commandPalette.formatPalette(commandName);
                            writer.println(palette);
                            writer.flush();
                        }
                );
    }

    private void registerBuiltinCommands() {
        commandRegistry.register("help", "사용 가능한 커맨드 목록을 표시합니다",
                args -> System.out.println(commandPalette.formatPalette(null)));

        commandRegistry.register("quit", "CLI를 종료합니다",
                args -> {
                    System.out.println("안녕히 가세요!");
                    System.exit(0);
                });

        commandRegistry.register("exit", "CLI를 종료합니다",
                args -> {
                    System.out.println("안녕히 가세요!");
                    System.exit(0);
                });

        commandRegistry.register("status", "서버 연결 상태를 확인합니다",
                args -> System.out.println("서버 상태 확인 기능은 추후 구현 예정입니다."));
    }

    private void ensureHistoryDirectory() throws IOException {
        Path historyPath = Path.of(cliConfig.getHistoryFilePath());
        Path parentDir = historyPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
    }
}
