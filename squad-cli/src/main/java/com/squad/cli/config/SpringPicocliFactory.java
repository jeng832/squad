package com.squad.cli.config;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Spring DI 컨테이너와 Picocli IFactory를 연결하는 브릿지.
 *
 * <p>Picocli가 커맨드 객체를 생성할 때 Spring ApplicationContext에서
 * 빈을 조회하여 의존성을 주입받은 인스턴스를 반환한다.
 * 빈으로 등록되지 않은 클래스는 기본 생성자로 인스턴스를 생성한다.</p>
 */
@Component
public class SpringPicocliFactory implements CommandLine.IFactory {

    private final ApplicationContext applicationContext;

    public SpringPicocliFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        try {
            return applicationContext.getBean(cls);
        } catch (Exception e) {
            return CommandLine.defaultFactory().create(cls);
        }
    }
}
