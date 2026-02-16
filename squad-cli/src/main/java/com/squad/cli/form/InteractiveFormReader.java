package com.squad.cli.form;

import com.squad.cli.shell.CommandContext;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * 번호로 항목을 선택받는다.
     *
     * <p>옵션 목록을 번호와 함께 표시하고 사용자가 번호를 입력한다.</p>
     *
     * @param ctx     커맨드 컨텍스트
     * @param prompt  선택 프롬프트
     * @param options 선택 옵션 목록
     * @return 선택된 인덱스 (0-based), 취소 시 -1
     */
    public int readSelection(CommandContext ctx, String prompt, List<String> options) {
        PrintWriter writer = ctx.writer();
        writer.println(prompt + ":");
        for (int i = 0; i < options.size(); i++) {
            writer.printf("  %d) %s%n", i + 1, options.get(i));
        }
        writer.flush();

        try {
            String input = ctx.lineReader().readLine("선택 (1-" + options.size() + "): ").trim();
            int selection = Integer.parseInt(input);
            if (selection >= 1 && selection <= options.size()) {
                return selection - 1;
            }
            writer.println("잘못된 선택입니다.");
            writer.flush();
            return -1;
        } catch (NumberFormatException e) {
            writer.println("숫자를 입력해주세요.");
            writer.flush();
            return -1;
        } catch (UserInterruptException | EndOfFileException e) {
            return -1;
        }
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
        try {
            String input = ctx.lineReader().readLine(prompt + " (y/n): ").trim().toLowerCase();
            return "y".equals(input) || "yes".equals(input);
        } catch (UserInterruptException | EndOfFileException e) {
            return false;
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
