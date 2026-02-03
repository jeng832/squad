package com.squad.mcp.repository;

import com.squad.mcp.domain.Mcp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class McpRepositoryTest {

    @Autowired
    private McpRepository mcpRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Mcp buildMcp(String name) {
        return Mcp.builder()
                .name(name)
                .description(name + " 설명")
                .config(Map.of(
                        "type", "stdio",
                        "command", "npx",
                        "args", List.of("-y", "mcp-server-" + name)
                ))
                .build();
    }

    @Test
    void Mcp_저장_후_ID로_조회_시_저장된_Mcp_반환() {
        Mcp mcp = buildMcp("github");

        Mcp saved = entityManager.persistFlushPop(mcp);

        Optional<Mcp> found = mcpRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("github");
        assertThat(found.get().getDescription()).isEqualTo("github 설명");
        assertThat(found.get().getConfig()).containsEntry("type", "stdio");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void Mcp_이름으로_조회_시_해당_Mcp_반환() {
        entityManager.persistFlushPop(buildMcp("github"));
        entityManager.persistFlushPop(buildMcp("filesystem"));

        Optional<Mcp> found = mcpRepository.findByName("filesystem");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("filesystem");
    }

    @Test
    void Mcp_존재하지_않는_이름으로_조회_시_빈_결과_반환() {
        Optional<Mcp> found = mcpRepository.findByName("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void Mcp_전체_조회_시_저장된_Mcp_목록_반환() {
        mcpRepository.save(buildMcp("github"));
        mcpRepository.save(buildMcp("filesystem"));
        mcpRepository.save(buildMcp("database"));

        List<Mcp> mcps = mcpRepository.findAll();

        assertThat(mcps).hasSize(3);
    }

    @Test
    void Mcp_삭제_후_조회_시_빈_결과_반환() {
        Mcp mcp = entityManager.persistFlushPop(buildMcp("github"));

        mcpRepository.deleteById(mcp.getId());
        mcpRepository.flush();

        assertThat(mcpRepository.findById(mcp.getId())).isEmpty();
    }

    @Test
    void config_JSON_저장_후_조회_시_원본_데이터_반환() {
        Map<String, Object> config = Map.of(
                "type", "sse",
                "url", "https://mcp.example.com/sse",
                "headers", Map.of("Authorization", "Bearer token123")
        );
        Mcp mcp = Mcp.builder()
                .name("custom-mcp")
                .config(config)
                .build();

        Mcp saved = entityManager.persistFlushPop(mcp);

        Mcp found = mcpRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getConfig()).containsEntry("type", "sse");
        assertThat(found.getConfig()).containsEntry("url", "https://mcp.example.com/sse");
    }

    @Test
    void Mcp_정보_수정_후_저장_시_수정된_값_반환() {
        Mcp mcp = entityManager.persistFlushPop(buildMcp("github"));
        Map<String, Object> updatedConfig = Map.of("type", "sse", "url", "https://new.example.com");

        mcp = mcpRepository.findById(mcp.getId()).orElseThrow();
        mcp.update("github-v2", "업데이트된 설명", updatedConfig);
        mcpRepository.flush();

        Mcp found = mcpRepository.findById(mcp.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("github-v2");
        assertThat(found.getDescription()).isEqualTo("업데이트된 설명");
        assertThat(found.getConfig()).containsEntry("type", "sse");
    }

    @Test
    void 중복_이름으로_저장_시_예외_발생() {
        entityManager.persistFlushPop(buildMcp("github"));

        assertThatThrownBy(() -> {
            mcpRepository.save(buildMcp("github"));
            mcpRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
