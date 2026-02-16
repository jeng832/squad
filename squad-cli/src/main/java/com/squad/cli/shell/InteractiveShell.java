package com.squad.cli.shell;

import com.squad.cli.config.CliConfig;
import com.squad.cli.util.FuzzySearchEngine;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
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
    private final FuzzySearchEngine fuzzySearchEngine;

    public InteractiveShell(CliConfig cliConfig,
                            CommandRegistry commandRegistry,
                            SlashCommandPalette commandPalette,
                            FuzzySearchEngine fuzzySearchEngine) {
        this.cliConfig = cliConfig;
        this.commandRegistry = commandRegistry;
        this.commandPalette = commandPalette;
        this.fuzzySearchEngine = fuzzySearchEngine;
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

            SlashCommandCompleter completer = new SlashCommandCompleter(commandRegistry, fuzzySearchEngine);

            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .variable(LineReader.HISTORY_FILE, cliConfig.getHistoryFilePath())
                    .option(LineReader.Option.AUTO_LIST, true)
                    .option(LineReader.Option.AUTO_MENU, true)
                    .option(LineReader.Option.LIST_AMBIGUOUS, false)
                    .build();

            bindSlashAutoComplete(lineReader);

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
        commandRegistry.register("status2", "테스트 용입니다.",
                args -> System.out.println("테스트 용 입니다."));
    }

    private void bindSlashAutoComplete(LineReader lineReader) {
        lineReader.getWidgets().put("slash-auto-complete", () -> {
            lineReader.getBuffer().write('/');
            lineReader.callWidget(LineReader.COMPLETE_WORD);
            return true;
        });

        lineReader.getWidgets().put("slash-menu-down", () -> {
            if (lineReader.getBuffer().toString().startsWith("/")) {
                lineReader.callWidget(LineReader.MENU_COMPLETE);
            } else {
                lineReader.callWidget(LineReader.DOWN_LINE_OR_HISTORY);
            }
            return true;
        });

        KeyMap<Binding> keyMap = lineReader.getKeyMaps().get(LineReader.MAIN);
        keyMap.bind(new Reference("slash-auto-complete"), "/");
        keyMap.bind(new Reference("slash-menu-down"), KeyMap.key(lineReader.getTerminal(), InfoCmp.Capability.key_down));

        KeyMap<Binding> menuKeyMap = lineReader.getKeyMaps().get("menu");
        if (menuKeyMap != null) {
            lineReader.getWidgets().put("menu-accept-and-execute", () -> {
                lineReader.callWidget(LineReader.ACCEPT_LINE);
                return true;
            });
            menuKeyMap.bind(new Reference("menu-accept-and-execute"), "\r");
            menuKeyMap.bind(new Reference("menu-accept-and-execute"), "\n");
        }
    }

    private void ensureHistoryDirectory() throws IOException {
        Path historyPath = Path.of(cliConfig.getHistoryFilePath());
        Path parentDir = historyPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
    }
}
