package com.squad.cli.mcp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Squad MCP 서버(stdio) 진입점.
 */
@SpringBootApplication(scanBasePackages = {
        "com.squad.cli.api",
        "com.squad.cli.config",
        "com.squad.cli.mcp"
})
public class SquadMcpServerApplication implements CommandLineRunner {

    private final SquadMcpStdioServer stdioServer;

    public SquadMcpServerApplication(SquadMcpStdioServer stdioServer) {
        this.stdioServer = stdioServer;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SquadMcpServerApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        System.exit(SpringApplication.exit(app.run(args)));
    }

    @Override
    public void run(String... args) {
        stdioServer.run();
    }
}
