package com.squad.mcp.gateway;

import com.squad.common.exception.NotFoundException;
import com.squad.llm.model.LlmTool;
import com.squad.llm.tool.LlmToolResult;
import com.squad.mcp.domain.Mcp;
import com.squad.mcp.gateway.dto.McpGatewayEvent;
import com.squad.mcp.repository.McpRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("McpGatewayService 단위 테스트")
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

    @Test
    @DisplayName("callTool에 arguments가 null이면 빈 Map으로 대체된다")
    void callTool_nullArguments() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        when(executor.execute(any()))
                .thenReturn(new LlmToolResult("id-1", "tool1", "ok"));

        McpGatewayService service = new McpGatewayService(repository, registry, executor);
        service.callTool("tool1", null);

        ArgumentCaptor<com.squad.llm.model.LlmToolCall> captor = ArgumentCaptor.forClass(com.squad.llm.model.LlmToolCall.class);
        verify(executor).execute(captor.capture());
        assertThat(captor.getValue().arguments()).isEmpty();
    }

    @Test
    @DisplayName("mcpName이 null이면 전체 도구 목록을 반환한다")
    void getTools_nullMcpName() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        when(registry.getAllTools()).thenReturn(List.of(new LlmTool("tool1", "", Map.of())));

        McpGatewayService service = new McpGatewayService(repository, registry, executor);
        List<LlmTool> tools = service.getTools(null);

        assertThat(tools).hasSize(1);
        verify(registry).getAllTools();
    }

    @Test
    @DisplayName("mcpName이 blank이면 전체 도구 목록을 반환한다")
    void getTools_blankMcpName() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        when(registry.getAllTools()).thenReturn(List.of());

        McpGatewayService service = new McpGatewayService(repository, registry, executor);
        List<LlmTool> tools = service.getTools("   ");

        assertThat(tools).isEmpty();
        verify(registry).getAllTools();
    }

    @Test
    @DisplayName("mcpName을 지정하면 해당 MCP의 도구만 반환한다")
    void getTools_specificMcpName() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        when(registry.getTools("github")).thenReturn(List.of(new LlmTool("github__search", "", Map.of())));

        McpGatewayService service = new McpGatewayService(repository, registry, executor);
        List<LlmTool> tools = service.getTools("github");

        assertThat(tools).hasSize(1);
        verify(registry).getTools("github");
    }

    @Test
    @DisplayName("streamEvents는 이벤트를 SSE로 스트리밍한다")
    void streamEvents_publishedEventsAreStreamed() {
        McpRepository repository = Mockito.mock(McpRepository.class);
        McpToolRegistry registry = Mockito.mock(McpToolRegistry.class);
        McpToolExecutor executor = Mockito.mock(McpToolExecutor.class);

        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("test-mcp")
                .description("테스트")
                .config(Map.of("command", "npx"))
                .build();
        when(repository.findByName("test-mcp")).thenReturn(Optional.of(mcp));
        when(registry.registerMcp(any())).thenReturn(List.of());

        McpGatewayService service = new McpGatewayService(repository, registry, executor);

        Flux<ServerSentEvent<McpGatewayEvent>> eventStream = service.streamEvents();

        // register 호출로 이벤트 발행
        service.register("test-mcp");

        StepVerifier.create(eventStream.take(1))
                .assertNext(sse -> {
                    assertThat(sse.event()).isEqualTo("MCP_REGISTERED");
                    assertThat(sse.data()).isNotNull();
                    assertThat(sse.data().mcpName()).isEqualTo("test-mcp");
                })
                .verifyComplete();
    }
}
