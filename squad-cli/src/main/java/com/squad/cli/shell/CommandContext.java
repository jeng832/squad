package com.squad.cli.shell;

import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;

/**
 * 커맨드 실행 시 전달되는 컨텍스트.
 *
 * <p>커맨드가 터미널과 상호작용하기 위해 필요한 JLine3 객체들을 제공한다.
 * 대화형 폼 입력, 출력 등에 활용된다.</p>
 *
 * @param lineReader JLine3 LineReader (사용자 입력 읽기)
 * @param terminal   JLine3 Terminal
 * @param writer     출력용 PrintWriter
 */
public record CommandContext(
        LineReader lineReader,
        Terminal terminal,
        PrintWriter writer
) {
}
