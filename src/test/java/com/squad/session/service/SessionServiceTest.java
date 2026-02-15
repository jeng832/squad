package com.squad.session.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import com.squad.session.dto.SessionCreateRequest;
import com.squad.session.dto.SessionResponse;
import com.squad.session.repository.SessionRepository;
import com.squad.squad.domain.Squad;
import com.squad.squad.repository.SquadRepository;
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
@DisplayName("SessionService 단위 테스트")
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SquadRepository squadRepository;

    @InjectMocks
    private SessionService sessionService;

    private Squad createSquad() {
        Agent orchestrator = Agent.builder()
                .id(1L).name("orch").roleType(RoleType.ORCHESTRATOR)
                .role("역할").llmConfig(Map.of("provider", "claude"))
                .build();
        return Squad.builder()
                .id(1L).name("squad").orchestrator(orchestrator).build();
    }

    private Session createSession(Long id, SessionStatus status) {
        return Session.builder()
                .id(id)
                .squad(createSquad())
                .userPrompt("테스트 프롬프트")
                .status(status)
                .build();
    }

    @Test
    @DisplayName("전체 세션 목록을 조회한다")
    void findAll() {
        given(sessionRepository.findAll()).willReturn(List.of(
                createSession(1L, SessionStatus.PENDING),
                createSession(2L, SessionStatus.RUNNING)
        ));

        List<SessionResponse> result = sessionService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).status()).isEqualTo(SessionStatus.PENDING);
    }

    @Test
    @DisplayName("빈 목록 조회 시 빈 리스트를 반환한다")
    void findAllEmpty() {
        given(sessionRepository.findAll()).willReturn(List.of());

        assertThat(sessionService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ID로 세션을 조회한다")
    void findById() {
        given(sessionRepository.findById(1L)).willReturn(Optional.of(createSession(1L, SessionStatus.PENDING)));

        SessionResponse result = sessionService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.userPrompt()).isEqualTo("테스트 프롬프트");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 NotFoundException 발생")
    void findByIdNotFound() {
        given(sessionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.findById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("세션을 생성한다")
    void create() {
        Squad squad = createSquad();
        given(squadRepository.findById(1L)).willReturn(Optional.of(squad));
        Session saved = createSession(1L, SessionStatus.PENDING);
        given(sessionRepository.save(any(Session.class))).willReturn(saved);

        SessionCreateRequest request = new SessionCreateRequest(1L, "새 프롬프트");
        SessionResponse result = sessionService.create(request);

        assertThat(result.status()).isEqualTo(SessionStatus.PENDING);
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    @DisplayName("존재하지 않는 squadId로 생성 시 NotFoundException 발생")
    void createSquadNotFound() {
        given(squadRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.create(new SessionCreateRequest(99L, "프롬프트")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("PENDING 상태의 세션을 취소한다")
    void cancelPending() {
        Session session = createSession(1L, SessionStatus.PENDING);
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        SessionResponse result = sessionService.cancel(1L);

        assertThat(result.status()).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    @DisplayName("RUNNING 상태의 세션을 취소한다")
    void cancelRunning() {
        Session session = createSession(1L, SessionStatus.RUNNING);
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        SessionResponse result = sessionService.cancel(1L);

        assertThat(result.status()).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    @DisplayName("COMPLETED 상태의 세션 취소 시 ValidationException 발생")
    void cancelCompleted() {
        Session session = createSession(1L, SessionStatus.COMPLETED);
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.cancel(1L))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("CANCELLED 상태의 세션 취소 시 ValidationException 발생")
    void cancelAlreadyCancelled() {
        Session session = createSession(1L, SessionStatus.CANCELLED);
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.cancel(1L))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("존재하지 않는 세션 취소 시 NotFoundException 발생")
    void cancelNotFound() {
        given(sessionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.cancel(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
