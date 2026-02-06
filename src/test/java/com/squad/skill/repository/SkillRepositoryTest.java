package com.squad.skill.repository;

import com.squad.skill.domain.Skill;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class SkillRepositoryTest {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Skill buildSkill(String name) {
        return Skill.builder()
                .name(name)
                .description(name + " 설명")
                .prompt("You are a " + name + " agent.")
                .requiredMcps(List.of(1L, 2L))
                .build();
    }

    @Test
    void Skill_저장_후_ID로_조회_시_저장된_Skill_반환() {
        Skill skill = buildSkill("summarizer");

        Skill saved = entityManager.persistFlushFind(skill);

        Optional<Skill> found = skillRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("summarizer");
        assertThat(found.get().getDescription()).isEqualTo("summarizer 설명");
        assertThat(found.get().getPrompt()).isEqualTo("You are a summarizer agent.");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void Skill_이름으로_조회_시_해당_Skill_반환() {
        entityManager.persistFlushFind(buildSkill("summarizer"));
        entityManager.persistFlushFind(buildSkill("translator"));

        Optional<Skill> found = skillRepository.findByName("translator");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("translator");
    }

    @Test
    void Skill_존재하지_않는_이름으로_조회_시_빈_결과_반환() {
        Optional<Skill> found = skillRepository.findByName("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void Skill_전체_조회_시_저장된_Skill_목록_반환() {
        skillRepository.save(buildSkill("summarizer"));
        skillRepository.save(buildSkill("translator"));
        skillRepository.save(buildSkill("analyzer"));

        List<Skill> skills = skillRepository.findAll();

        assertThat(skills).hasSize(3);
    }

    @Test
    void Skill_삭제_후_조회_시_빈_결과_반환() {
        Skill skill = entityManager.persistFlushFind(buildSkill("summarizer"));

        skillRepository.deleteById(skill.getId());
        skillRepository.flush();

        assertThat(skillRepository.findById(skill.getId())).isEmpty();
    }

    @Test
    void required_mcps_JSON_저장_후_조회_시_원본_데이터_반환() {
        Skill skill = Skill.builder()
                .name("custom-skill")
                .prompt("Custom prompt.")
                .requiredMcps(List.of(10L, 20L, 30L))
                .build();

        Skill saved = entityManager.persistFlushFind(skill);

        Skill found = skillRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getRequiredMcps()).hasSize(3);
        assertThat(found.getRequiredMcps().stream().map(Number::longValue)).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    void Skill_정보_수정_후_저장_시_수정된_값_반환() {
        Skill skill = entityManager.persistFlushFind(buildSkill("summarizer"));

        skill = skillRepository.findById(skill.getId()).orElseThrow();
        skill.update("summarizer-v2", "업데이트된 설명", "Updated prompt.", List.of(5L));
        skillRepository.flush();

        Skill found = skillRepository.findById(skill.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("summarizer-v2");
        assertThat(found.getDescription()).isEqualTo("업데이트된 설명");
        assertThat(found.getPrompt()).isEqualTo("Updated prompt.");
        assertThat(found.getRequiredMcps().stream().map(Number::longValue)).containsExactly(5L);
    }

    @Test
    void 중복_이름으로_저장_시_예외_발생() {
        entityManager.persistFlushFind(buildSkill("summarizer"));

        assertThatThrownBy(() -> {
            skillRepository.save(buildSkill("summarizer"));
            skillRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
