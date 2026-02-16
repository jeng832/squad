package com.squad.cli;

import com.squad.cli.config.SpringPicocliFactory;
import com.squad.cli.shell.InteractiveShell;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

/**
 * Squad CLI 애플리케이션 진입점.
 *
 * <p>Spring Boot를 DI 컨테이너로만 사용하며 (web-application-type: none),
 * Picocli 기반 커맨드라인 인터페이스를 실행한다.</p>
 *
 * <ul>
 *   <li>인자 없음: 인터랙티브 셸(REPL) 모드 진입</li>
 *   <li>인자 있음: One-shot 커맨드 실행</li>
 * </ul>
 */
@SpringBootApplication
public class SquadCliApplication implements CommandLineRunner {

    private final SquadCliCommand squadCliCommand;
    private final SpringPicocliFactory picocliFactory;
    private final InteractiveShell interactiveShell;

    public SquadCliApplication(SquadCliCommand squadCliCommand,
                               SpringPicocliFactory picocliFactory,
                               InteractiveShell interactiveShell) {
        this.squadCliCommand = squadCliCommand;
        this.picocliFactory = picocliFactory;
        this.interactiveShell = interactiveShell;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(
                SpringApplication.run(SquadCliApplication.class, args)
        ));
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            interactiveShell.start();
        } else {
            CommandLine commandLine = new CommandLine(squadCliCommand, picocliFactory);
            int exitCode = commandLine.execute(args);
            if (exitCode != 0) {
                throw new RuntimeException("CLI 명령 실행 실패 (exit code: " + exitCode + ")");
            }
        }
    }
}
