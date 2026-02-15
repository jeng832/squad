package com.squad.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

/**
 * Picocli 최상위 커맨드.
 *
 * <p>서브커맨드 없이 실행하면 사용법을 출력한다.
 * 인터랙티브 모드는 {@link com.squad.cli.shell.InteractiveShell}이 담당한다.</p>
 */
@Component
@Command(
        name = "squad",
        mixinStandardHelpOptions = true,
        version = "squad-cli 0.1.0",
        description = "Squad 에이전트 오케스트레이션 CLI"
)
public class SquadCliCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}
