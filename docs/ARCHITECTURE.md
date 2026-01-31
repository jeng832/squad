# Squad 시스템 아키텍처 설계서

## 1. 개요

### 1.1 문서 목적
이 문서는 Squad 플랫폼의 전체 시스템 아키텍처와 개별 모듈의 설계를 정의합니다.

### 1.2 시스템 개요
Squad는 멀티 AI 에이전트 협업 플랫폼으로, 여러 AI 에이전트가 Orchestrator의 조율 하에 복잡한 작업을 수행합니다.

### 1.3 기술 스택
| 항목 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build | Gradle |
| Database | MySQL 8.x |
| Container | Docker |
| API | REST API |
| Real-time | WebSocket (STOMP) |

---

## 2. 시스템 전체 아키텍처

### 2.1 High-Level 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client Layer                                    │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │    Web UI       │  │   REST API      │  │   WebSocket     │              │
│  │   (React)       │  │   Clients       │  │   Clients       │              │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘              │
└───────────│────────────────────│────────────────────│────────────────────────┘
            │                    │                    │
            ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Gateway                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    Spring Boot Application                           │    │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐        │    │
│  │  │ Agent     │  │ Squad     │  │ Session   │  │ WebSocket │        │    │
│  │  │ Controller│  │ Controller│  │ Controller│  │ Controller│        │    │
│  │  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘        │    │
│  └────────│──────────────│──────────────│──────────────│───────────────┘    │
└───────────│──────────────│──────────────│──────────────│────────────────────┘
            │              │              │              │
            ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Service Layer                                   │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐ │
│  │ Agent     │  │ Squad     │  │ Session   │  │ Message   │  │ Execution │ │
│  │ Service   │  │ Service   │  │ Service   │  │ Service   │  │ Service   │ │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘ │
└────────│──────────────│──────────────│──────────────│──────────────│────────┘
         │              │              │              │              │
         ▼              ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Core Layer                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │  Agent Engine   │  │  LLM Provider   │  │  MCP Connector  │              │
│  │                 │  │  (Claude, etc)  │  │                 │              │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘              │
└───────────│────────────────────│────────────────────│────────────────────────┘
            │                    │                    │
            ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Infrastructure Layer                                │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐                 │
│  │  MySQL    │  │  Redis    │  │  Secret   │  │  Docker   │                 │
│  │  (Data)   │  │  (Cache)  │  │  Store    │  │  Runtime  │                 │
│  └───────────┘  └───────────┘  └───────────┘  └───────────┘                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 컴포넌트 다이어그램

```
┌──────────────────────────────────────────────────────────────────┐
│                         Squad Application                         │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      Presentation Layer                      │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │ │
│  │  │ AgentController│ │SquadController│ │SessionController│      │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘       │ │
│  │  ┌──────────────┐  ┌──────────────┐                         │ │
│  │  │ MCPController │  │SkillController│                        │ │
│  │  └──────────────┘  └──────────────┘                         │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                               │                                   │
│                               ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      Application Layer                       │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │ │
│  │  │ AgentService │  │ SquadService │  │SessionService│       │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘       │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │ │
│  │  │ExecutionService│ │MessageService│  │MonitorService│       │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘       │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                               │                                   │
│                               ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                        Domain Layer                          │ │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐         │ │
│  │  │  Agent  │  │  Squad  │  │ Session │  │ Message │         │ │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘         │ │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐                      │ │
│  │  │   MCP   │  │  Skill  │  │Conversation│                    │ │
│  │  └─────────┘  └─────────┘  └─────────┘                      │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                               │                                   │
│                               ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    Infrastructure Layer                      │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │ │
│  │  │ AgentRepository│ │LlmProviderImpl│ │MCPConnector │       │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘       │ │
│  │  ┌──────────────┐  ┌──────────────┐                         │ │
│  │  │ SecretStore  │  │ EventPublisher│                        │ │
│  │  └──────────────┘  └──────────────┘                         │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. 패키지 구조

```
com.squad
├── SquadApplication.java
├── config/                          # 설정 클래스
│   ├── WebConfig.java
│   ├── WebSocketConfig.java
│   ├── SecurityConfig.java
│   └── LlmConfig.java
│
├── api/                             # Presentation Layer
│   ├── controller/
│   │   ├── AgentController.java
│   │   ├── SquadController.java
│   │   ├── SessionController.java
│   │   ├── McpController.java
│   │   └── SkillController.java
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   └── websocket/
│       └── SessionWebSocketHandler.java
│
├── application/                     # Application Layer
│   ├── service/
│   │   ├── AgentService.java
│   │   ├── SquadService.java
│   │   ├── SessionService.java
│   │   ├── ExecutionService.java
│   │   ├── MessageService.java
│   │   └── MonitorService.java
│   └── usecase/
│       ├── CreateAgentUseCase.java
│       ├── StartSessionUseCase.java
│       └── ExecuteAgentUseCase.java
│
├── domain/                          # Domain Layer
│   ├── model/
│   │   ├── agent/
│   │   │   ├── Agent.java
│   │   │   ├── AgentId.java
│   │   │   ├── RoleType.java
│   │   │   └── LlmConfig.java
│   │   ├── squad/
│   │   │   ├── Squad.java
│   │   │   ├── SquadId.java
│   │   │   └── DirectCommunicationRule.java
│   │   ├── session/
│   │   │   ├── Session.java
│   │   │   ├── SessionId.java
│   │   │   └── SessionStatus.java
│   │   ├── message/
│   │   │   ├── Message.java
│   │   │   ├── MessageId.java
│   │   │   └── Conversation.java
│   │   ├── mcp/
│   │   │   ├── Mcp.java
│   │   │   └── McpConfig.java
│   │   └── skill/
│   │       └── Skill.java
│   ├── repository/
│   │   ├── AgentRepository.java
│   │   ├── SquadRepository.java
│   │   ├── SessionRepository.java
│   │   └── MessageRepository.java
│   └── event/
│       ├── AgentExecutionStarted.java
│       ├── AgentExecutionCompleted.java
│       └── MessageSent.java
│
├── infrastructure/                  # Infrastructure Layer
│   ├── persistence/
│   │   ├── entity/
│   │   │   ├── AgentEntity.java
│   │   │   ├── SquadEntity.java
│   │   │   └── SessionEntity.java
│   │   ├── repository/
│   │   │   ├── JpaAgentRepository.java
│   │   │   └── JpaSquadRepository.java
│   │   └── mapper/
│   │       └── AgentMapper.java
│   ├── llm/
│   │   ├── LlmProvider.java
│   │   ├── LlmProviderFactory.java
│   │   ├── claude/
│   │   │   ├── ClaudeLlmProvider.java
│   │   │   ├── ClaudeApiClient.java
│   │   │   └── ClaudeMessageMapper.java
│   │   ├── openai/
│   │   │   └── OpenAiLlmProvider.java
│   │   └── gemini/
│   │       └── GeminiLlmProvider.java
│   ├── mcp/
│   │   ├── McpConnector.java
│   │   └── McpProcessManager.java
│   ├── secret/
│   │   ├── SecretStore.java
│   │   └── AesSecretStore.java
│   └── event/
│       └── SpringEventPublisher.java
│
└── common/                          # 공통 유틸리티
    ├── exception/
    │   ├── SquadException.java
    │   ├── AgentNotFoundException.java
    │   └── LlmApiException.java
    └── util/
        └── JsonUtils.java
```

---

## 4. 핵심 모듈 상세 설계

### 4.1 Agent 모듈

#### 4.1.1 도메인 모델

```java
@Entity
@Table(name = "agents")
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleType roleType;

    @Column(columnDefinition = "TEXT")
    private String role;  // System prompt

    @Embedded
    private LlmConfig llmConfig;

    @ElementCollection
    @CollectionTable(name = "agent_mcps")
    private Set<String> mcpIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "agent_skills")
    private Set<String> skillIds = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

public enum RoleType {
    ORCHESTRATOR,
    WORKER,
    ANALYST,
    SCRIBE,
    CUSTOM
}

@Embeddable
public class LlmConfig {
    private String provider;  // "claude", "openai", "gemini"
    private String model;
    private String apiKeyRef;  // "ref:secret/xxx"

    @Column(name = "llm_temperature")
    private Double temperature;

    @Column(name = "llm_max_tokens")
    private Integer maxTokens;
}
```

#### 4.1.2 서비스 인터페이스

```java
public interface AgentService {

    Agent createAgent(CreateAgentCommand command);

    Agent updateAgent(String id, UpdateAgentCommand command);

    void deleteAgent(String id);

    Agent getAgent(String id);

    List<Agent> listAgents();

    List<Agent> findByRoleType(RoleType roleType);
}

@Data
@Builder
public class CreateAgentCommand {
    private String name;
    private RoleType roleType;
    private String role;
    private LlmConfig llmConfig;
    private Set<String> mcpIds;
    private Set<String> skillIds;
}
```

### 4.2 Squad 모듈

#### 4.2.1 도메인 모델

```java
@Entity
@Table(name = "squads")
public class Squad {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String orchestratorId;  // Agent ID

    @ElementCollection
    @CollectionTable(name = "squad_agents")
    private Set<String> agentIds = new HashSet<>();

    @Embedded
    private DirectCommunicationConfig directCommunication;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Validation
    public void validate(AgentRepository agentRepository) {
        Agent orchestrator = agentRepository.findById(orchestratorId)
            .orElseThrow(() -> new AgentNotFoundException(orchestratorId));

        if (orchestrator.getRoleType() != RoleType.ORCHESTRATOR) {
            throw new InvalidSquadConfigException(
                "Orchestrator must have ORCHESTRATOR role type"
            );
        }
    }
}

@Embeddable
public class DirectCommunicationConfig {
    private boolean enabled;

    @ElementCollection
    @CollectionTable(name = "direct_communication_rules")
    private List<DirectCommunicationRule> rules = new ArrayList<>();
}

@Embeddable
public class DirectCommunicationRule {
    private String fromAgentId;
    private String toAgentId;
    private boolean allowed;
}
```

### 4.3 Session 모듈

#### 4.3.1 도메인 모델

```java
@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String squadId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String userPrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(columnDefinition = "TEXT")
    private String result;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    private List<Message> messages = new ArrayList<>();

    // 세션 시작
    public void start() {
        this.status = SessionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    // 세션 완료
    public void complete(String result) {
        this.status = SessionStatus.COMPLETED;
        this.result = result;
        this.completedAt = LocalDateTime.now();
    }

    // 세션 실패
    public void fail(String error) {
        this.status = SessionStatus.FAILED;
        this.result = error;
        this.completedAt = LocalDateTime.now();
    }
}

public enum SessionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

#### 4.3.2 세션 실행 서비스

```java
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final SessionRepository sessionRepository;
    private final SquadRepository squadRepository;
    private final AgentRepository agentRepository;
    private final LlmProviderFactory llmProviderFactory;
    private final MessageService messageService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    public void executeSession(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow();

        session.start();
        sessionRepository.save(session);

        try {
            Squad squad = squadRepository.findById(session.getSquadId())
                .orElseThrow();

            Agent orchestrator = agentRepository
                .findById(squad.getOrchestratorId())
                .orElseThrow();

            // Orchestrator 실행
            String result = executeOrchestrator(
                session,
                orchestrator,
                squad,
                session.getUserPrompt()
            );

            session.complete(result);
        } catch (Exception e) {
            session.fail(e.getMessage());
        }

        sessionRepository.save(session);
    }

    private String executeOrchestrator(
            Session session,
            Agent orchestrator,
            Squad squad,
            String userPrompt
    ) {
        OrchestratorContext context = new OrchestratorContext(
            session, squad, agentRepository
        );

        // Orchestrator에게 초기 프롬프트 전달
        LlmRequest request = buildOrchestratorRequest(
            orchestrator, userPrompt, context
        );

        LlmProvider provider = llmProviderFactory
            .getProvider(orchestrator.getLlmConfig().getProvider());

        // 반복적으로 Orchestrator와 상호작용
        while (!context.isCompleted()) {
            LlmResponse response = provider.sendMessage(request);

            // Tool Call 처리 (에이전트 호출)
            if (response.hasToolCalls()) {
                for (ToolCall call : response.getToolCalls()) {
                    String toolResult = handleToolCall(
                        session, squad, call, context
                    );
                    context.addToolResult(call.getId(), toolResult);
                }
                request = buildFollowUpRequest(orchestrator, context);
            } else {
                // 완료
                context.setCompleted(true);
                return response.getContent();
            }
        }

        return context.getFinalResult();
    }

    private String handleToolCall(
            Session session,
            Squad squad,
            ToolCall call,
            OrchestratorContext context
    ) {
        if ("delegate_task".equals(call.getName())) {
            String agentId = call.getArguments().get("agent_id");
            String task = call.getArguments().get("task");

            Agent agent = agentRepository.findById(agentId).orElseThrow();

            // 에이전트 실행
            eventPublisher.publishEvent(
                new AgentExecutionStarted(session.getId(), agentId)
            );

            String result = executeAgent(session, agent, task);

            eventPublisher.publishEvent(
                new AgentExecutionCompleted(session.getId(), agentId, result)
            );

            return result;
        }

        throw new UnknownToolException(call.getName());
    }
}
```

### 4.4 LLM Provider 모듈

#### 4.4.1 Provider 인터페이스

```java
public interface LlmProvider {

    LlmResponse sendMessage(LlmRequest request);

    Flux<LlmStreamChunk> streamMessage(LlmRequest request);

    String getProviderName();

    List<String> getSupportedModels();

    boolean supportsToolUse();
}

@Data
@Builder
public class LlmRequest {
    private String model;
    private String systemPrompt;
    private List<LlmMessage> messages;
    private List<Tool> tools;
    private Integer maxTokens;
    private Double temperature;
    private String apiKey;
}

@Data
@Builder
public class LlmResponse {
    private String id;
    private String content;
    private String finishReason;
    private List<ToolCall> toolCalls;
    private LlmUsage usage;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
```

#### 4.4.2 Claude Provider 구현

```java
@Component
@RequiredArgsConstructor
public class ClaudeLlmProvider implements LlmProvider {

    private final ClaudeApiClient apiClient;
    private final ClaudeMessageMapper mapper;

    @Override
    public String getProviderName() {
        return "claude";
    }

    @Override
    public List<String> getSupportedModels() {
        return List.of(
            "claude-opus-4-5-20251101",
            "claude-sonnet-4-20250514",
            "claude-haiku-3-5-20241022"
        );
    }

    @Override
    public boolean supportsToolUse() {
        return true;
    }

    @Override
    public LlmResponse sendMessage(LlmRequest request) {
        ClaudeRequest claudeRequest = mapper.toClaudeRequest(request);
        ClaudeResponse claudeResponse = apiClient.sendMessage(
            request.getApiKey(),
            claudeRequest
        );
        return mapper.toLlmResponse(claudeResponse);
    }

    @Override
    public Flux<LlmStreamChunk> streamMessage(LlmRequest request) {
        ClaudeRequest claudeRequest = mapper.toClaudeRequest(request);
        claudeRequest.setStream(true);

        return apiClient.streamMessage(request.getApiKey(), claudeRequest)
            .map(mapper::toLlmStreamChunk);
    }
}
```

### 4.5 MCP Connector 모듈

#### 4.5.1 MCP 관리

```java
@Service
@RequiredArgsConstructor
public class McpConnector {

    private final McpRepository mcpRepository;
    private final McpProcessManager processManager;

    public McpConnection connect(String mcpId) {
        Mcp mcp = mcpRepository.findById(mcpId)
            .orElseThrow(() -> new McpNotFoundException(mcpId));

        return processManager.startProcess(mcp);
    }

    public void disconnect(McpConnection connection) {
        processManager.stopProcess(connection);
    }

    public ToolResult executeTool(
            McpConnection connection,
            String toolName,
            Map<String, Object> arguments
    ) {
        return connection.callTool(toolName, arguments);
    }
}

@Component
public class McpProcessManager {

    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public McpConnection startProcess(Mcp mcp) {
        McpConfig config = mcp.getConfig();

        ProcessBuilder builder = new ProcessBuilder();
        builder.command(buildCommand(config));
        builder.environment().putAll(resolveEnv(config.getEnv()));

        try {
            Process process = builder.start();
            processes.put(mcp.getId(), process);

            return new StdioMcpConnection(
                mcp.getId(),
                process.getInputStream(),
                process.getOutputStream()
            );
        } catch (IOException e) {
            throw new McpStartException(mcp.getId(), e);
        }
    }

    private List<String> buildCommand(McpConfig config) {
        List<String> command = new ArrayList<>();
        command.add(config.getCommand());
        command.addAll(config.getArgs());
        return command;
    }
}
```

### 4.6 Message & Conversation 모듈

#### 4.6.1 메시지 도메인

```java
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private Session session;

    @Column(nullable = false)
    private String fromAgentId;

    @Column(nullable = false)
    private String toAgentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageType type;

    private LocalDateTime createdAt;
}

public enum MessageType {
    TASK_REQUEST,      // 작업 요청
    TASK_RESPONSE,     // 작업 응답
    HELP_REQUEST,      // 도움 요청
    HELP_RESPONSE,     // 도움 응답
    STATUS_UPDATE      // 상태 업데이트
}
```

#### 4.6.2 Conversation 관리

```java
@Service
@RequiredArgsConstructor
public class ConversationManager {

    private final MessageRepository messageRepository;

    /**
     * 특정 에이전트의 대화 히스토리를 LLM 요청 형식으로 변환
     */
    public List<LlmMessage> buildConversationHistory(
            String sessionId,
            String agentId
    ) {
        List<Message> messages = messageRepository
            .findBySessionIdAndAgentId(sessionId, agentId);

        return messages.stream()
            .map(this::toLlmMessage)
            .collect(Collectors.toList());
    }

    private LlmMessage toLlmMessage(Message message) {
        String role = message.getToAgentId().equals(getCurrentAgentId())
            ? "user"
            : "assistant";

        return LlmMessage.builder()
            .role(role)
            .content(message.getContent())
            .build();
    }
}
```

---

## 5. 데이터베이스 설계

### 5.1 ERD

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│    agents    │       │    squads    │       │   sessions   │
├──────────────┤       ├──────────────┤       ├──────────────┤
│ id (PK)      │       │ id (PK)      │       │ id (PK)      │
│ name         │       │ name         │       │ squad_id (FK)│
│ role_type    │◄──────│ orchestrator │       │ user_prompt  │
│ role         │       │ description  │◄──────│ status       │
│ llm_provider │       │ direct_comm  │       │ result       │
│ llm_model    │       │ created_at   │       │ started_at   │
│ llm_api_key  │       │ updated_at   │       │ completed_at │
│ created_at   │       └──────────────┘       └──────┬───────┘
│ updated_at   │              │                      │
└──────────────┘              │                      │
       │                      ▼                      ▼
       │              ┌──────────────┐       ┌──────────────┐
       │              │ squad_agents │       │   messages   │
       │              ├──────────────┤       ├──────────────┤
       └──────────────│ squad_id(FK) │       │ id (PK)      │
                      │ agent_id(FK) │       │ session_id   │
                      └──────────────┘       │ from_agent   │
                                             │ to_agent     │
┌──────────────┐       ┌──────────────┐      │ content      │
│     mcps     │       │    skills    │      │ type         │
├──────────────┤       ├──────────────┤      │ created_at   │
│ id (PK)      │       │ id (PK)      │      └──────────────┘
│ name         │       │ name         │
│ description  │       │ description  │
│ config (JSON)│       │ prompt       │
│ created_at   │       │ required_mcp │
└──────────────┘       │ created_at   │
                       └──────────────┘

┌──────────────┐
│   secrets    │
├──────────────┤
│ id (PK)      │
│ name         │
│ value (AES)  │
│ created_at   │
│ updated_at   │
└──────────────┘
```

### 5.2 테이블 정의

```sql
-- Agents
CREATE TABLE agents (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    role_type VARCHAR(50) NOT NULL,
    role TEXT,
    llm_provider VARCHAR(50) NOT NULL,
    llm_model VARCHAR(100) NOT NULL,
    llm_api_key_ref VARCHAR(255),
    llm_temperature DECIMAL(3,2),
    llm_max_tokens INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Agent MCPs (Many-to-Many)
CREATE TABLE agent_mcps (
    agent_id VARCHAR(36) NOT NULL,
    mcp_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (agent_id, mcp_id),
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
);

-- Agent Skills (Many-to-Many)
CREATE TABLE agent_skills (
    agent_id VARCHAR(36) NOT NULL,
    skill_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (agent_id, skill_id),
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
);

-- Squads
CREATE TABLE squads (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    orchestrator_id VARCHAR(36) NOT NULL,
    direct_comm_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (orchestrator_id) REFERENCES agents(id)
);

-- Squad Agents (Many-to-Many)
CREATE TABLE squad_agents (
    squad_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (squad_id, agent_id),
    FOREIGN KEY (squad_id) REFERENCES squads(id) ON DELETE CASCADE,
    FOREIGN KEY (agent_id) REFERENCES agents(id)
);

-- Direct Communication Rules
CREATE TABLE direct_communication_rules (
    id VARCHAR(36) PRIMARY KEY,
    squad_id VARCHAR(36) NOT NULL,
    from_agent_id VARCHAR(36) NOT NULL,
    to_agent_id VARCHAR(36) NOT NULL,
    allowed BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (squad_id) REFERENCES squads(id) ON DELETE CASCADE
);

-- Sessions
CREATE TABLE sessions (
    id VARCHAR(36) PRIMARY KEY,
    squad_id VARCHAR(36) NOT NULL,
    user_prompt TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    result TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (squad_id) REFERENCES squads(id)
);

-- Messages
CREATE TABLE messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    from_agent_id VARCHAR(36) NOT NULL,
    to_agent_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    INDEX idx_session_id (session_id),
    INDEX idx_from_agent (from_agent_id),
    INDEX idx_to_agent (to_agent_id)
);

-- MCPs
CREATE TABLE mcps (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    config JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Skills
CREATE TABLE skills (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    prompt TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Skill Required MCPs
CREATE TABLE skill_required_mcps (
    skill_id VARCHAR(36) NOT NULL,
    mcp_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (skill_id, mcp_id),
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE CASCADE
);

-- Secrets (Encrypted)
CREATE TABLE secrets (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    encrypted_value TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

---

## 6. API 설계

### 6.1 REST API 엔드포인트

#### Agent API
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/v1/agents | 에이전트 목록 조회 |
| POST | /api/v1/agents | 에이전트 생성 |
| GET | /api/v1/agents/{id} | 에이전트 상세 조회 |
| PUT | /api/v1/agents/{id} | 에이전트 수정 |
| DELETE | /api/v1/agents/{id} | 에이전트 삭제 |

#### Squad API
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/v1/squads | Squad 목록 조회 |
| POST | /api/v1/squads | Squad 생성 |
| GET | /api/v1/squads/{id} | Squad 상세 조회 |
| PUT | /api/v1/squads/{id} | Squad 수정 |
| DELETE | /api/v1/squads/{id} | Squad 삭제 |

#### Session API
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/v1/sessions | 세션 목록 조회 |
| POST | /api/v1/sessions | 세션 시작 |
| GET | /api/v1/sessions/{id} | 세션 상세 조회 |
| POST | /api/v1/sessions/{id}/cancel | 세션 취소 |
| GET | /api/v1/sessions/{id}/messages | 세션 메시지 조회 |

#### MCP API
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/v1/mcps | MCP 목록 조회 |
| POST | /api/v1/mcps | MCP 등록 |
| GET | /api/v1/mcps/{id} | MCP 상세 조회 |
| PUT | /api/v1/mcps/{id} | MCP 수정 |
| DELETE | /api/v1/mcps/{id} | MCP 삭제 |

#### Skill API
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /api/v1/skills | Skill 목록 조회 |
| POST | /api/v1/skills | Skill 등록 |
| GET | /api/v1/skills/{id} | Skill 상세 조회 |
| PUT | /api/v1/skills/{id} | Skill 수정 |
| DELETE | /api/v1/skills/{id} | Skill 삭제 |

### 6.2 WebSocket API

#### 연결
```
ws://localhost:8080/ws/sessions/{sessionId}
```

#### 메시지 타입
```json
// Server → Client: 에이전트 상태 변경
{
  "type": "AGENT_STATUS",
  "agentId": "agent-001",
  "status": "RUNNING",
  "currentTask": "코드 분석 중..."
}

// Server → Client: 메시지 전송
{
  "type": "MESSAGE",
  "from": "orchestrator-001",
  "to": "analyst-001",
  "content": "이 코드를 분석해줘",
  "timestamp": "2026-01-31T10:30:00Z"
}

// Server → Client: 세션 완료
{
  "type": "SESSION_COMPLETE",
  "result": "분석 결과...",
  "timestamp": "2026-01-31T10:35:00Z"
}
```

---

## 7. 실행 환경 설계

### 7.1 Docker Compose 구성

```yaml
version: '3.8'

services:
  squad-app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_NAME=squad
      - DB_USER=squad
      - DB_PASSWORD=${DB_PASSWORD}
      - SECRET_ENCRYPTION_KEY=${SECRET_ENCRYPTION_KEY}
    depends_on:
      - mysql
      - redis
    networks:
      - squad-network

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=squad
      - MYSQL_USER=squad
      - MYSQL_PASSWORD=${DB_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - squad-network

  redis:
    image: redis:7-alpine
    networks:
      - squad-network

networks:
  squad-network:
    driver: bridge

volumes:
  mysql-data:
```

### 7.2 Agent 컨테이너 (향후 확장)

현재 MVP에서는 모든 에이전트가 단일 애플리케이션 내에서 실행됩니다.
향후 확장 시 각 에이전트를 별도 컨테이너로 분리할 수 있습니다.

---

## 8. 보안 설계

### 8.1 Secret 관리

```java
@Service
@RequiredArgsConstructor
public class AesSecretStore implements SecretStore {

    private final SecretRepository secretRepository;

    @Value("${squad.secret.encryption-key}")
    private String encryptionKey;

    @Override
    public String resolveSecret(String reference) {
        // "ref:secret/my-api-key" 형식 파싱
        if (!reference.startsWith("ref:secret/")) {
            return reference;  // 일반 값
        }

        String secretName = reference.substring("ref:secret/".length());
        Secret secret = secretRepository.findByName(secretName)
            .orElseThrow(() -> new SecretNotFoundException(secretName));

        return decrypt(secret.getEncryptedValue());
    }

    private String decrypt(String encryptedValue) {
        // AES-256 복호화
        // ...
    }
}
```

### 8.2 API Key 보안
- API Key는 암호화하여 DB에 저장
- 환경변수로 복호화 키 관리
- 로그에 API Key 노출 방지

---

## 9. MVP 개발 계획

### Phase 1: Core (MVP)
1. Agent CRUD
2. Squad CRUD
3. Session 실행 (기본)
4. Claude LLM Provider
5. 기본 WebSocket 모니터링

### Phase 2: Enhancement
1. OpenAI Provider 추가
2. MCP 연동
3. Skill 관리
4. 상세 모니터링 대시보드

### Phase 3: Advanced
1. Gemini Provider 추가
2. 직접 통신 규칙
3. 메시지 흐름 시각화
4. 세션 히스토리 분석
