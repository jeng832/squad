package com.squad.mcp.gateway;

import com.squad.common.exception.NotFoundException;
import com.squad.llm.model.LlmTool;
import com.squad.llm.tool.LlmToolResult;
import com.squad.mcp.domain.Mcp;
import com.squad.mcp.repository.McpRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpGatewayServiceTest {

    @Test
    void 이름으로_MCP를_등록한다() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("github")
                .description("GitHub MCP")
                .config(Map.of("command", "npx"))
                .build();

        when(repository.findByName("github")).thenReturn(Optional.of(mcp));
        when(registry.registerMcp(any())).thenReturn(List.of(new LlmTool("github__search", "", Map.of())));

        McpGatewayService service = new McpGatewayService(repository, registry, executor);
        List<LlmTool> tools = service.register("github");

        assertThat(tools).hasSize(1);
        verify(registry).registerMcp(any());
    }

    @Test
    void 존재하지_않는_MCP_등록시_예외() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        when(repository.findByName("missing")).thenReturn(Optional.empty());

        McpGatewayService service = new McpGatewayService(repository, registry, executor);

        assertThatThrownBy(() -> service.register("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 이름으로_MCP를_해제한다() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("github")
                .description("GitHub MCP")
                .config(Map.of("command", "npx"))
                .build();
        when(repository.findByName("github")).thenReturn(Optional.of(mcp));

        McpGatewayService service = new McpGatewayService(repository, registry, executor);
        service.unregister("github");

        verify(registry).unregisterMcp("github");
    }

    @Test
    void 존재하지_않는_MCP_해제시_예외() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        when(repository.findByName("missing")).thenReturn(Optional.empty());

        McpGatewayService service = new McpGatewayService(repository, registry, executor);

        assertThatThrownBy(() -> service.unregister("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void Tool을_호출한다() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        when(executor.execute(any()))
                .thenReturn(new LlmToolResult("id-1", "github__search", "ok"));

        McpGatewayService service = new McpGatewayService(repository, registry, executor);
        LlmToolResult result = service.callTool("github__search", Map.of("q", "repo"));

        assertThat(result.output()).isEqualTo("ok");

        ArgumentCaptor<com.squad.llm.model.LlmToolCall> captor = ArgumentCaptor.forClass(com.squad.llm.model.LlmToolCall.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("github__search");
        assertThat(captor.getValue().arguments()).containsEntry("q", "repo");
    }
}
