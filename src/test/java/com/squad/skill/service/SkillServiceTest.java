package com.squad.skill.service;

import com.squad.common.exception.NotFoundException;
import com.squad.skill.domain.Skill;
import com.squad.skill.dto.SkillCreateRequest;
import com.squad.skill.dto.SkillResponse;
import com.squad.skill.dto.SkillUpdateRequest;
import com.squad.skill.repository.SkillRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkillService 단위 테스트")
class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @InjectMocks
    private SkillService skillService;

    private Skill createSkill(Long id, String name) {
        return Skill.builder()
                .id(id)
                .name(name)
                .description("테스트 Skill")
                .prompt("테스트 프롬프트")
                .requiredMcps(List.of(1L, 2L))
                .build();
    }

    @Test
    @DisplayName("전체 Skill 목록을 조회한다")
    void findAll() {
        given(skillRepository.findAll()).willReturn(List.of(createSkill(1L, "skill-1"), createSkill(2L, "skill-2")));

        List<SkillResponse> result = skillService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("skill-1");
    }

    @Test
    @DisplayName("빈 목록 조회 시 빈 리스트를 반환한다")
    void findAllEmpty() {
        given(skillRepository.findAll()).willReturn(List.of());

        assertThat(skillService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ID로 Skill을 조회한다")
    void findById() {
        given(skillRepository.findById(1L)).willReturn(Optional.of(createSkill(1L, "skill-1")));

        SkillResponse result = skillService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("skill-1");
        assertThat(result.prompt()).isEqualTo("테스트 프롬프트");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 NotFoundException 발생")
    void findByIdNotFound() {
        given(skillRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> skillService.findById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Skill을 생성한다")
    void create() {
        SkillCreateRequest request = new SkillCreateRequest("new-skill", "설명", "프롬프트", List.of(1L));
        Skill saved = createSkill(1L, "new-skill");
        given(skillRepository.save(any(Skill.class))).willReturn(saved);

        SkillResponse result = skillService.create(request);

        assertThat(result.name()).isEqualTo("new-skill");
        verify(skillRepository).save(any(Skill.class));
    }

    @Test
    @DisplayName("requiredMcps가 null인 Skill을 생성한다")
    void createWithNullRequiredMcps() {
        SkillCreateRequest request = new SkillCreateRequest("new-skill", "설명", "프롬프트", null);
        Skill saved = Skill.builder()
                .id(1L).name("new-skill").description("설명").prompt("프롬프트").requiredMcps(null).build();
        given(skillRepository.save(any(Skill.class))).willReturn(saved);

        SkillResponse result = skillService.create(request);

        assertThat(result.name()).isEqualTo("new-skill");
        assertThat(result.requiredMcps()).isNull();
    }

    @Test
    @DisplayName("Skill을 수정한다")
    void update() {
        Skill skill = createSkill(1L, "skill-1");
        given(skillRepository.findById(1L)).willReturn(Optional.of(skill));
        SkillUpdateRequest request = new SkillUpdateRequest("updated-skill", "수정", "새 프롬프트", List.of(3L));

        SkillResponse result = skillService.update(1L, request);

        assertThat(result.name()).isEqualTo("updated-skill");
        assertThat(skill.getPrompt()).isEqualTo("새 프롬프트");
    }

    @Test
    @DisplayName("존재하지 않는 Skill 수정 시 NotFoundException 발생")
    void updateNotFound() {
        given(skillRepository.findById(99L)).willReturn(Optional.empty());
        SkillUpdateRequest request = new SkillUpdateRequest("name", "설명", "프롬프트", null);

        assertThatThrownBy(() -> skillService.update(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Skill을 삭제한다")
    void delete() {
        given(skillRepository.findById(1L)).willReturn(Optional.of(createSkill(1L, "skill-1")));

        skillService.delete(1L);

        verify(skillRepository).deleteById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 Skill 삭제 시 NotFoundException 발생")
    void deleteNotFound() {
        given(skillRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> skillService.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
