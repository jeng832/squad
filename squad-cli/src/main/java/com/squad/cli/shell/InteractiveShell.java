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
import java.util.List;

/**
 * JLine3 기반 인터랙티브 REPL 셸.
 *
 * <p>프롬프트를 표시하고 사용자 입력을 받아 슬래시 커맨드를 실행한다.
 * Tab 자동완성, 히스토리, 커스텀 화살표 키 선택 UI를 지원한다.</p>
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
                    .option(LineReader.Option.AUTO_MENU, false)
                    .option(LineReader.Option.LIST_AMBIGUOUS, false)
                    .build();

            bindSlashAutoComplete(lineReader, completer);

            PrintWriter writer = terminal.writer();
            CommandContext ctx = new CommandContext(lineReader, terminal, writer);

            writer.println("Squad CLI v0.1.0 - '/help'로 사용 가능한 커맨드를 확인하세요.");
            writer.println("종료: Ctrl+D 또는 /quit");
            writer.flush();

            runLoop(ctx);
        }
    }

    private void runLoop(CommandContext ctx) {
        while (true) {
            try {
                String line = ctx.lineReader().readLine(cliConfig.getPrompt()).trim();
                if (line.isEmpty()) {
                    continue;
                }
                processInput(ctx, line);
            } catch (UserInterruptException e) {
                // Ctrl+C: 현재 입력 취소, 루프 계속
            } catch (EndOfFileException e) {
                ctx.writer().println("안녕히 가세요!");
                ctx.writer().flush();
                return;
            }
        }
    }

    private void processInput(CommandContext ctx, String line) {
        if (line.startsWith("/")) {
            executeSlashCommand(ctx, line);
        } else {
            ctx.writer().println("알 수 없는 입력입니다. '/help'로 사용 가능한 커맨드를 확인하세요.");
            ctx.writer().flush();
        }
    }

    private void executeSlashCommand(CommandContext ctx, String line) {
        String withoutSlash = line.substring(1);
        String[] parts = withoutSlash.split("\\s+", 2);
        String commandName = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        commandRegistry.find(commandName)
                .ifPresentOrElse(
                        entry -> entry.executor().execute(ctx, args),
                        () -> {
                            String palette = commandPalette.formatPalette(commandName);
                            ctx.writer().println(palette);
                            ctx.writer().flush();
                        }
                );
    }

    private void registerBuiltinCommands() {
        commandRegistry.register("help", "사용 가능한 커맨드 목록을 표시합니다",
                (ctx, args) -> {
                    ctx.writer().println(commandPalette.formatPalette(null));
                    ctx.writer().flush();
                });

        commandRegistry.register("quit", "CLI를 종료합니다",
                (ctx, args) -> {
                    ctx.writer().println("안녕히 가세요!");
                    ctx.writer().flush();
                    System.exit(0);
                });

        commandRegistry.register("exit", "CLI를 종료합니다",
                (ctx, args) -> {
                    ctx.writer().println("안녕히 가세요!");
                    ctx.writer().flush();
                    System.exit(0);
                });

        commandRegistry.register("status", "서버 연결 상태를 확인합니다",
                (ctx, args) -> {
                    ctx.writer().println("서버 상태 확인 기능은 추후 구현 예정입니다.");
                    ctx.writer().flush();
                });
    }

    private void bindSlashAutoComplete(LineReader lineReader, SlashCommandCompleter completer) {
        lineReader.getWidgets().put("slash-auto-complete", () -> {
            completer.resetSelection();
            lineReader.getBuffer().write('/');
            lineReader.callWidget(LineReader.COMPLETE_WORD);
            return true;
        });

        lineReader.getWidgets().put("slash-menu-down", () -> {
            String buffer = lineReader.getBuffer().toString();
            if (buffer.startsWith("/") || completer.isInSubcommandLevel()) {
                if (completer.isInSubcommandLevel()) {
                    completer.selectNext();
                    lineReader.callWidget(LineReader.LIST_CHOICES);
                    return true;
                }

                String query = buffer.substring(1);

                if (completer.isNavigating() && !query.equals(completer.getAnchorQuery())) {
                    completer.resetSelection();
                }

                if (!completer.isNavigating()) {
                    List<String> allNames = commandRegistry.getAll().keySet().stream()
                            .sorted()
                            .toList();
                    List<String> matched = query.isEmpty()
                            ? allNames
                            : fuzzySearchEngine.search(query, allNames).stream()
                                    .sorted()
                                    .toList();
                    if (matched.isEmpty()) {
                        return true;
                    }
                    completer.startNavigation(query, matched);
                } else {
                    completer.selectNext();
                }
                lineReader.callWidget(LineReader.LIST_CHOICES);
            } else {
                lineReader.callWidget(LineReader.DOWN_LINE_OR_HISTORY);
            }
            return true;
        });

        lineReader.getWidgets().put("slash-menu-up", () -> {
            String buffer = lineReader.getBuffer().toString();
            if (completer.isInSubcommandLevel()) {
                completer.selectPrevious();
                lineReader.callWidget(LineReader.LIST_CHOICES);
            } else if (buffer.startsWith("/") && completer.isNavigating()) {
                completer.selectPrevious();
                lineReader.callWidget(LineReader.LIST_CHOICES);
            } else {
                lineReader.callWidget(LineReader.UP_LINE_OR_HISTORY);
            }
            return true;
        });

        lineReader.getWidgets().put("slash-accept", () -> {
            if (completer.isInSubcommandLevel()) {
                completer.getSelectedName().ifPresent(subName -> {
                    String fullCommand = "/" + completer.getParentCommandName() + " " + subName;
                    lineReader.getBuffer().clear();
                    lineReader.getBuffer().write(fullCommand);
                });
                completer.resetSelection();
                lineReader.callWidget(LineReader.ACCEPT_LINE);
                return true;
            }

            completer.getSelectedName().ifPresent(name -> {
                CommandRegistry.CommandEntry entry = commandRegistry.find(name).orElse(null);
                if (entry != null && entry.hasSubcommands()) {
                    lineReader.getBuffer().clear();
                    lineReader.getBuffer().write("/" + name);
                    completer.enterSubcommandLevel(name);
                } else {
                    lineReader.getBuffer().clear();
                    lineReader.getBuffer().write("/" + name);
                    completer.resetSelection();
                }
            });

            if (completer.isInSubcommandLevel()) {
                lineReader.callWidget(LineReader.LIST_CHOICES);
            } else {
                lineReader.callWidget(LineReader.ACCEPT_LINE);
            }
            return true;
        });

        lineReader.getWidgets().put("slash-escape", () -> {
            lineReader.getBuffer().clear();
            completer.resetSelection();
            lineReader.callWidget(LineReader.REDISPLAY);
            return true;
        });

        KeyMap<Binding> keyMap = lineReader.getKeyMaps().get(LineReader.MAIN);
        keyMap.bind(new Reference("slash-auto-complete"), "/");
        keyMap.bind(new Reference("slash-menu-down"),
                KeyMap.key(lineReader.getTerminal(), InfoCmp.Capability.key_down));
        keyMap.bind(new Reference("slash-menu-up"),
                KeyMap.key(lineReader.getTerminal(), InfoCmp.Capability.key_up));
        keyMap.bind(new Reference("slash-accept"), "\r");
        keyMap.bind(new Reference("slash-accept"), "\n");
        keyMap.bind(new Reference("slash-escape"), "\u001b");
    }

    private void ensureHistoryDirectory() throws IOException {
        Path historyPath = Path.of(cliConfig.getHistoryFilePath());
        Path parentDir = historyPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
    }
}
