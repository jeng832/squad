package com.squad.mcp.service;

import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.mcp.domain.Mcp;
import com.squad.mcp.dto.McpCreateRequest;
import com.squad.mcp.dto.McpResponse;
import com.squad.mcp.dto.McpUpdateRequest;
import com.squad.mcp.repository.McpRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpService 단위 테스트")
class McpServiceTest {

    @Mock
    private McpRepository mcpRepository;

    @InjectMocks
    private McpService mcpService;

    private Mcp createMcp(Long id, String name) {
        return Mcp.builder()
                .id(id)
                .name(name)
                .description("테스트 MCP")
                .config(Map.of("command", "npx", "args", List.of("test")))
                .build();
    }

    @Test
    @DisplayName("전체 MCP 목록을 조회한다")
    void findAll() {
        given(mcpRepository.findAll()).willReturn(List.of(createMcp(1L, "mcp-1"), createMcp(2L, "mcp-2")));

        List<McpResponse> result = mcpService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("mcp-1");
    }

    @Test
    @DisplayName("빈 목록 조회 시 빈 리스트를 반환한다")
    void findAllEmpty() {
        given(mcpRepository.findAll()).willReturn(List.of());

        assertThat(mcpService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ID로 MCP를 조회한다")
    void findById() {
        given(mcpRepository.findById(1L)).willReturn(Optional.of(createMcp(1L, "mcp-1")));

        McpResponse result = mcpService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("mcp-1");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 NotFoundException 발생")
    void findByIdNotFound() {
        given(mcpRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mcpService.findById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("MCP를 생성한다")
    void create() {
        McpCreateRequest request = new McpCreateRequest("new-mcp", "설명", Map.of("command", "npx"));
        Mcp saved = createMcp(1L, "new-mcp");
        given(mcpRepository.save(any(Mcp.class))).willReturn(saved);

        McpResponse result = mcpService.create(request);

        assertThat(result.name()).isEqualTo("new-mcp");
        verify(mcpRepository).save(any(Mcp.class));
    }

    @Test
    @DisplayName("config에 command가 없으면 생성 시 ValidationException 발생")
    void createWithoutCommand() {
        McpCreateRequest request = new McpCreateRequest("mcp", "설명", Map.of("args", List.of("test")));

        assertThatThrownBy(() -> mcpService.create(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("config의 command가 null이면 생성 시 ValidationException 발생")
    void createWithNullCommand() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("command", null);
        McpCreateRequest request = new McpCreateRequest("mcp", "설명", config);

        assertThatThrownBy(() -> mcpService.create(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("MCP를 수정한다")
    void update() {
        Mcp mcp = createMcp(1L, "mcp-1");
        given(mcpRepository.findById(1L)).willReturn(Optional.of(mcp));
        McpUpdateRequest request = new McpUpdateRequest("updated-mcp", "수정", Map.of("command", "node"));

        McpResponse result = mcpService.update(1L, request);

        assertThat(result.name()).isEqualTo("updated-mcp");
    }

    @Test
    @DisplayName("존재하지 않는 MCP 수정 시 NotFoundException 발생")
    void updateNotFound() {
        given(mcpRepository.findById(99L)).willReturn(Optional.empty());
        McpUpdateRequest request = new McpUpdateRequest("name", "설명", Map.of("command", "npx"));

        assertThatThrownBy(() -> mcpService.update(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("config에 command가 없으면 수정 시 ValidationException 발생")
    void updateWithoutCommand() {
        McpUpdateRequest request = new McpUpdateRequest("mcp", "설명", Map.of("args", List.of()));

        assertThatThrownBy(() -> mcpService.update(1L, request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("MCP를 삭제한다")
    void delete() {
        given(mcpRepository.findById(1L)).willReturn(Optional.of(createMcp(1L, "mcp-1")));

        mcpService.delete(1L);

        verify(mcpRepository).deleteById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 MCP 삭제 시 NotFoundException 발생")
    void deleteNotFound() {
        given(mcpRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> mcpService.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("config가 null이면 생성 시 NullPointerException 발생")
    void createWithNullConfig() {
        McpCreateRequest request = new McpCreateRequest("mcp", "설명", null);

        assertThatThrownBy(() -> mcpService.create(request))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("MCP 수정 시 엔티티의 update 메서드가 호출된다")
    void updateCallsMcpUpdate() {
        Mcp mcp = createMcp(1L, "mcp-1");
        given(mcpRepository.findById(1L)).willReturn(Optional.of(mcp));
        McpUpdateRequest request = new McpUpdateRequest("updated", "새 설명", Map.of("command", "node"));

        mcpService.update(1L, request);

        assertThat(mcp.getName()).isEqualTo("updated");
        assertThat(mcp.getDescription()).isEqualTo("새 설명");
    }
}
