package com.squad.cli.form;

import com.squad.cli.shell.CommandContext;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 대화형 폼 입력 유틸리티.
 *
 * <p>사용자로부터 텍스트, 선택, 확인 등의 입력을 대화형으로 받는다.
 * Agent, MCP, Squad 등 다양한 엔티티 관리 커맨드에서 재사용된다.</p>
 */
@Component
public class InteractiveFormReader {

    /**
     * 한 줄 텍스트를 입력받는다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 입력 프롬프트
     * @return 사용자 입력 문자열, 취소 시 null
     */
    public String readLine(CommandContext ctx, String prompt) {
        return readLine(ctx, prompt, null);
    }

    /**
     * 한 줄 텍스트를 입력받는다. 기본값을 지원한다.
     *
     * <p>사용자가 빈 값을 입력하면 기본값이 반환된다.</p>
     *
     * @param ctx          커맨드 컨텍스트
     * @param prompt       입력 프롬프트
     * @param defaultValue 기본값 (null 가능)
     * @return 사용자 입력 문자열, 취소 시 null
     */
    public String readLine(CommandContext ctx, String prompt, String defaultValue) {
        String displayPrompt = defaultValue != null
                ? prompt + " (" + defaultValue + "): "
                : prompt + ": ";
        try {
            String input = ctx.lineReader().readLine(displayPrompt).trim();
            if (input.isEmpty() && defaultValue != null) {
                return defaultValue;
            }
            return input.isEmpty() ? null : input;
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }

    /**
     * 여러 줄 텍스트를 입력받는다.
     *
     * <p>빈 줄을 입력하면 입력이 완료된다.
     * {@code @파일경로} 형식으로 파일에서 읽을 수도 있다.</p>
     *
     * <p>사용자가 아무 입력 없이 빈 줄만 입력하면 빈 문자열을 반환하고,
     * Ctrl+C 등으로 취소하면 null을 반환한다.</p>
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 입력 프롬프트
     * @return 입력된 여러 줄 텍스트, 빈 입력 시 빈 문자열, 취소 시 null
     */
    public String readMultiLine(CommandContext ctx, String prompt) {
        PrintWriter writer = ctx.writer();
        writer.println(prompt + " (빈 줄로 입력 완료, @파일경로로 파일 읽기):");
        writer.flush();

        StringBuilder sb = new StringBuilder();
        try {
            while (true) {
                String line = ctx.lineReader().readLine("  | ");
                if (line.isEmpty()) {
                    break;
                }
                if (sb.isEmpty() && line.startsWith("@")) {
                    return readFromFile(writer, line.substring(1).trim());
                }
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(line);
            }
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }

        return sb.isEmpty() ? "" : sb.toString();
    }

    private static final int ESC = 0x1b;
    private static final int CTRL_C = 0x03;
    private static final int ENTER_CR = '\r';
    private static final int ENTER_LF = '\n';
    private static final int SPACE = ' ';
    private static final int ESC_TIMEOUT_MS = 50;

    /**
     * 화살표키로 항목을 선택받는다.
     *
     * <p>옵션 목록을 표시하고 화살표키(↑↓)로 이동, Enter로 확정, ESC/Ctrl+C로 취소한다.
     * 목록의 위/아래 끝에서 순환한다.</p>
     *
     * @param ctx     커맨드 컨텍스트
     * @param prompt  선택 프롬프트
     * @param options 선택 옵션 목록
     * @return 선택된 인덱스 (0-based), 취소 시 -1
     */
    public int readSelection(CommandContext ctx, String prompt, List<String> options) {
        Terminal terminal = ctx.terminal();
        PrintWriter writer = ctx.writer();
        int selectedIndex = 0;

        writer.println(prompt + ":");
        renderOptions(writer, options, selectedIndex);
        printNavigationHint(writer);
        writer.flush();

        Attributes originalAttributes = terminal.enterRawMode();
        try {
            while (true) {
                int key = terminal.reader().read();

                if (key == ENTER_CR || key == ENTER_LF) {
                    clearNavigationHint(writer);
                    return selectedIndex;
                }

                if (key == CTRL_C) {
                    clearNavigationHint(writer);
                    return -1;
                }

                if (key == ESC) {
                    int direction = readEscSequence(terminal);
                    if (direction == 0) {
                        clearNavigationHint(writer);
                        return -1;
                    }
                    selectedIndex = wrapIndex(selectedIndex + direction, options.size());
                    moveUpAndRedraw(writer, options, selectedIndex);
                }
            }
        } catch (IOException e) {
            return -1;
        } finally {
            terminal.setAttributes(originalAttributes);
        }
    }

    /**
     * 화살표키와 스페이스바로 여러 항목을 선택받는다.
     *
     * <p>옵션 목록을 체크박스 형태로 표시하고 화살표키(↑↓)로 이동,
     * 스페이스바로 선택/해제 토글, Enter로 확정, ESC/Ctrl+C로 취소한다.
     * 기존 선택 상태를 {@code preSelected}로 전달할 수 있다.</p>
     *
     * @param ctx         커맨드 컨텍스트
     * @param prompt      선택 프롬프트
     * @param options     선택 옵션 목록
     * @param preSelected 미리 선택된 인덱스 목록 (null 가능)
     * @return 선택된 인덱스 리스트 (0-based), 취소 시 null
     */
    public List<Integer> readMultiSelection(CommandContext ctx, String prompt,
                                            List<String> options, List<Integer> preSelected) {
        Terminal terminal = ctx.terminal();
        PrintWriter writer = ctx.writer();
        int cursorIndex = 0;
        boolean[] selected = new boolean[options.size()];

        if (preSelected != null) {
            for (int idx : preSelected) {
                if (idx >= 0 && idx < selected.length) {
                    selected[idx] = true;
                }
            }
        }

        writer.println(prompt + ":");
        renderMultiOptions(writer, options, selected, cursorIndex);
        printMultiNavigationHint(writer);
        writer.flush();

        Attributes originalAttributes = terminal.enterRawMode();
        try {
            while (true) {
                int key = terminal.reader().read();

                if (key == ENTER_CR || key == ENTER_LF) {
                    clearNavigationHint(writer);
                    return collectSelectedIndices(selected);
                }

                if (key == CTRL_C) {
                    clearNavigationHint(writer);
                    return null;
                }

                if (key == SPACE) {
                    selected[cursorIndex] = !selected[cursorIndex];
                    moveUpAndRedrawMulti(writer, options, selected, cursorIndex);
                    continue;
                }

                if (key == ESC) {
                    int direction = readEscSequence(terminal);
                    if (direction == 0) {
                        clearNavigationHint(writer);
                        return null;
                    }
                    cursorIndex = wrapIndex(cursorIndex + direction, options.size());
                    moveUpAndRedrawMulti(writer, options, selected, cursorIndex);
                }
            }
        } catch (IOException e) {
            return null;
        } finally {
            terminal.setAttributes(originalAttributes);
        }
    }

    private void renderMultiOptions(PrintWriter writer, List<String> options,
                                    boolean[] selected, int cursorIndex) {
        for (int i = 0; i < options.size(); i++) {
            String cursor = (i == cursorIndex) ? "> " : "  ";
            String check = selected[i] ? "[x] " : "[ ] ";
            writer.println(cursor + check + options.get(i));
        }
    }

    private void printMultiNavigationHint(PrintWriter writer) {
        writer.print("\033[90m↑↓ 이동 | Space 선택/해제 | Enter 확정 | ESC 취소\033[0m");
        writer.flush();
    }

    private void moveUpAndRedrawMulti(PrintWriter writer, List<String> options,
                                      boolean[] selected, int cursorIndex) {
        int linesToMoveUp = options.size();
        writer.print("\r\033[2K");
        writer.print("\033[" + linesToMoveUp + "A");
        for (int i = 0; i < options.size(); i++) {
            String cursor = (i == cursorIndex) ? "> " : "  ";
            String check = selected[i] ? "[x] " : "[ ] ";
            writer.print("\r\033[2K" + cursor + check + options.get(i));
            if (i < options.size() - 1) {
                writer.println();
            }
        }
        writer.println();
        printMultiNavigationHint(writer);
        writer.flush();
    }

    private List<Integer> collectSelectedIndices(boolean[] selected) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * ESC 시퀀스를 읽어 화살표키 방향을 판별한다.
     *
     * <p>ESC 이후 50ms 내에 '[' + 'A'/'B'가 오면 화살표키로 판단하고,
     * 타임아웃이면 ESC 단독(취소)으로 판단한다.</p>
     *
     * @param terminal JLine3 Terminal
     * @return -1(위), +1(아래), 0(ESC 단독/취소)
     * @throws IOException 읽기 실패 시
     */
    private int readEscSequence(Terminal terminal) throws IOException {
        int next = peekWithTimeout(terminal, ESC_TIMEOUT_MS);
        if (next != '[') {
            return 0;
        }
        int arrow = terminal.reader().read(ESC_TIMEOUT_MS);
        if (arrow == 'A') {
            return -1;
        }
        if (arrow == 'B') {
            return 1;
        }
        return 0;
    }

    private int peekWithTimeout(Terminal terminal, int timeoutMs) throws IOException {
        return terminal.reader().read(timeoutMs);
    }

    private void renderOptions(PrintWriter writer, List<String> options, int selectedIndex) {
        for (int i = 0; i < options.size(); i++) {
            String prefix = (i == selectedIndex) ? "> " : "  ";
            writer.println(prefix + options.get(i));
        }
    }

    private void printNavigationHint(PrintWriter writer) {
        writer.print("\033[90m↑↓ 이동 | Enter 선택 | ESC 취소\033[0m");
        writer.flush();
    }

    private void clearNavigationHint(PrintWriter writer) {
        writer.print("\r\033[2K");
        writer.flush();
    }

    /**
     * 커서를 옵션 목록 시작으로 올려 다시 그린다.
     *
     * @param writer        출력 PrintWriter
     * @param options       옵션 목록
     * @param selectedIndex 현재 선택 인덱스
     */
    private void moveUpAndRedraw(PrintWriter writer, List<String> options, int selectedIndex) {
        int linesToMoveUp = options.size();
        writer.print("\r\033[2K");
        writer.print("\033[" + linesToMoveUp + "A");
        for (int i = 0; i < options.size(); i++) {
            String prefix = (i == selectedIndex) ? "> " : "  ";
            writer.print("\r\033[2K" + prefix + options.get(i));
            if (i < options.size() - 1) {
                writer.println();
            }
        }
        writer.println();
        printNavigationHint(writer);
        writer.flush();
    }

    private int wrapIndex(int index, int size) {
        return ((index % size) + size) % size;
    }

    /**
     * 비밀 값을 마스킹하여 입력받는다.
     *
     * <p>입력 시 문자가 '*'로 표시되어 화면에 원본 값이 노출되지 않는다.
     * JLine 히스토리에도 기록되지 않는다.</p>
     *
     * @param ctx          커맨드 컨텍스트
     * @param prompt       입력 프롬프트
     * @param defaultValue 기본값 (null 가능)
     * @return 사용자 입력 문자열, 취소 시 null
     */
    public String readSecret(CommandContext ctx, String prompt, String defaultValue) {
        String displayPrompt = defaultValue != null
                ? prompt + " (" + maskPreview(defaultValue) + "): "
                : prompt + ": ";
        try {
            String input = ctx.lineReader().readLine(displayPrompt, '*').trim();
            if (input.isEmpty() && defaultValue != null) {
                return defaultValue;
            }
            return input.isEmpty() ? null : input;
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }

    /**
     * 비밀 값을 마스킹하여 입력받는다. 기본값 없음.
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 입력 프롬프트
     * @return 사용자 입력 문자열, 취소 시 null
     */
    public String readSecret(CommandContext ctx, String prompt) {
        return readSecret(ctx, prompt, null);
    }

    /**
     * 예/아니오 확인을 받는다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 확인 프롬프트
     * @return 예(true), 아니오(false)
     */
    public boolean readConfirm(CommandContext ctx, String prompt) {
        int selected = readSelection(ctx, prompt, List.of("예", "아니오"));
        return selected == 0;
    }

    /**
     * 외부 에디터를 열어 텍스트를 편집받는다.
     *
     * <p>에디터 결정 순서: {@code $VISUAL} → {@code $EDITOR} → {@code vi}.
     * 임시 파일에 초기 내용을 기록한 후 에디터를 실행하고,
     * 에디터 종료 후 파일 내용을 읽어 반환한다.</p>
     *
     * <p>JLine Terminal을 일시 정지하여 에디터가 터미널을 온전히 점유하도록 한다.</p>
     *
     * @param ctx             커맨드 컨텍스트
     * @param initialContent  에디터에 미리 채울 내용 (null이면 빈 파일)
     * @param fileExtension   임시 파일 확장자 (예: ".json")
     * @return 편집된 텍스트, 취소 또는 에러 시 null
     */
    public String readWithEditor(CommandContext ctx, String initialContent, String fileExtension) {
        PrintWriter writer = ctx.writer();
        Terminal terminal = ctx.terminal();

        String editor = resolveEditor();
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("squad-edit-", fileExtension);
            if (initialContent != null && !initialContent.isEmpty()) {
                Files.writeString(tempFile, initialContent);
            }

            writer.println("에디터를 여는 중... (" + editor + ")");
            writer.flush();

            terminal.pause();
            try {
                ProcessBuilder pb = new ProcessBuilder(editor, tempFile.toString());
                pb.inheritIO();
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    writer.println("에디터가 비정상 종료되었습니다. (exit code: " + exitCode + ")");
                    writer.flush();
                    return null;
                }
            } finally {
                terminal.resume();
            }

            String content = Files.readString(tempFile).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            writer.println("에디터 실행 실패: " + e.getMessage());
            writer.flush();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.println("에디터 실행이 중단되었습니다.");
            writer.flush();
            return null;
        } finally {
            deleteTempFile(tempFile);
        }
    }

    /**
     * JSON 입력 방법을 선택받아 JSON 문자열을 반환한다.
     *
     * <p>"에디터로 편집" / "직접 입력" / "건너뛰기" 3가지 선택지를 제공한다.
     * update 시 기존값을 전달하면 에디터에 미리 채워진다.</p>
     *
     * @param ctx           커맨드 컨텍스트
     * @param prompt        선택 프롬프트
     * @param template      에디터 초기 템플릿 (새 입력 시)
     * @param existingValue 기존 JSON 값 (update 시, null 가능)
     * @return JSON 문자열, 건너뛰기 또는 취소 시 null
     */
    public String readJsonInput(CommandContext ctx, String prompt,
                                String template, String existingValue) {
        String editorName = resolveEditor();
        List<String> options = List.of(
                "에디터로 편집 (" + editorName + ")",
                "직접 입력",
                "건너뛰기"
        );

        int selected = readSelection(ctx, prompt, options);
        if (selected < 0 || selected == 2) {
            return null;
        }

        if (selected == 0) {
            String editorContent = (existingValue != null && !existingValue.isEmpty())
                    ? existingValue : template;
            return readWithEditor(ctx, editorContent, ".json");
        }

        return readMultiLine(ctx, prompt);
    }

    /**
     * 현재 설정된 에디터 이름을 반환한다.
     *
     * <p>결정 순서: {@code $VISUAL} → {@code $EDITOR} → {@code vi}</p>
     *
     * @return 에디터 이름
     */
    public String resolveEditorName() {
        return resolveEditor();
    }

    private String resolveEditor() {
        String visual = System.getenv("VISUAL");
        if (visual != null && !visual.isBlank()) {
            return visual;
        }
        String editor = System.getenv("EDITOR");
        if (editor != null && !editor.isBlank()) {
            return editor;
        }
        return "vi";
    }

    private void deleteTempFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // 임시 파일 삭제 실패는 무시
            }
        }
    }

    private String maskPreview(String value) {
        if (value.startsWith("ref:secret/")) {
            return value;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private String readFromFile(PrintWriter writer, String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                writer.println("파일을 찾을 수 없습니다: " + filePath);
                writer.flush();
                return null;
            }
            String content = Files.readString(path);
            writer.println("파일에서 읽음: " + filePath + " (" + content.length() + "자)");
            writer.flush();
            return content;
        } catch (IOException e) {
            writer.println("파일 읽기 실패: " + e.getMessage());
            writer.flush();
            return null;
        }
    }
}
