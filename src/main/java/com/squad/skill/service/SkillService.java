package com.squad.skill.service;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.skill.domain.Skill;
import com.squad.skill.dto.SkillCreateRequest;
import com.squad.skill.dto.SkillResponse;
import com.squad.skill.dto.SkillUpdateRequest;
import com.squad.skill.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    public List<SkillResponse> findAll() {
        return skillRepository.findAll().stream()
                .map(SkillResponse::from)
                .toList();
    }

    public SkillResponse findById(Long id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SKILL_NOT_FOUND));
        return SkillResponse.from(skill);
    }

    @Transactional
    public SkillResponse create(SkillCreateRequest request) {
        Skill skill = Skill.builder()
                .name(request.name())
                .description(request.description())
                .prompt(request.prompt())
                .requiredMcps(request.requiredMcps())
                .build();
        return SkillResponse.from(skillRepository.save(skill));
    }

    @Transactional
    public SkillResponse update(Long id, SkillUpdateRequest request) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SKILL_NOT_FOUND));
        skill.update(request.name(), request.description(), request.prompt(), request.requiredMcps());
        return SkillResponse.from(skill);
    }

    @Transactional
    public void delete(Long id) {
        skillRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SKILL_NOT_FOUND));
        skillRepository.deleteById(id);
    }
}
