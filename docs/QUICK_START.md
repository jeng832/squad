# Squad Quick Start (최초 실행 가이드)

이 문서는 **처음 레포를 받은 사용자**가 로컬에서 Squad를 실행하는 최소 절차를 설명합니다.

## 1) 사전 준비

- Docker / Docker Compose 사용 가능
- Java 21
- (선택) Claude 연동 시 `CLAUDE_API_KEY`

## 2) 저장소 루트에서 인프라 실행

```bash
docker compose up -d
```

기본으로 MySQL(3306), Redis(6379)가 올라옵니다.

## 3) Agent 이미지 1회 빌드 (필수)

세션 시작 시 `squad-agent:latest` 이미지를 사용하므로 최초 1회 빌드가 필요합니다.

```bash
docker build -f docker/agent/Dockerfile -t squad-agent:latest .
```

확인:

```bash
docker image ls | grep squad-agent
```

최근 코드 반영 후 재실행 시에는 기존 이미지를 재사용하지 말고 재빌드를 권장합니다.

```bash
docker build --no-cache -f docker/agent/Dockerfile -t squad-agent:latest .
```

## 4) 서버 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 5) 세션 시작

- API/CLI/Web에서 일반적인 세션 시작 플로우를 사용합니다.
- 세션 시작 시 Agent 컨테이너가 동적으로 생성됩니다.

---

## 자주 발생하는 오류

### `No such image: squad-agent:latest`

원인:
- Agent 이미지가 아직 빌드되지 않음

해결:

```bash
docker build -f docker/agent/Dockerfile -t squad-agent:latest .
```

### `agent-runner.jar not found at /app/agent-runner.jar`

원인:
- 오래된/잘못된 이미지 태그를 사용 중

해결:

```bash
docker rmi squad-agent:latest
docker build -f docker/agent/Dockerfile -t squad-agent:latest .
```

### `Could not find or load main class com.squad.agent.runner.AgentToolCliApplication`

원인:
- 예전 `squad-agent:latest` 이미지(신규 Tool CLI 미포함)를 사용 중

해결:

```bash
docker build --no-cache -f docker/agent/Dockerfile -t squad-agent:latest .
```

그리고 기존 세션/컨테이너를 정리한 뒤 새 세션을 시작한다.

참고:
- 중지된 Agent 컨테이너는 Tool 실행 시 자동 재시작하지 않는다.
- `실행 대상 컨테이너가 실행 중이 아닙니다` 오류가 나면 기존 세션을 재사용하지 말고 새 세션을 시작한다.

### `fatal: could not read Username for 'https://github.com'`

원인:
- private 저장소를 `gitSecretName` 없이 clone 시도함
- 또는 Git Secret(PAT) 값이 잘못되었음

해결:
- Session 생성 시 `gitSecretName`에 유효한 GitHub PAT Secret 이름을 지정
- PAT에 최소 저장소 읽기 권한이 있는지 확인

### `bash_exec` 실행 시 "허용되지 않는 메타 문자" 오류

원인:
- `|`, `&&`, `;`, `$()` 같은 shell 연산자를 포함한 명령을 요청함

해결:
- 단일 allowlist 명령 형태로 요청하거나, 복합 검색은 `file_search` 도구 사용
- 최신 Tool 제약을 반영하려면 Agent 이미지를 재빌드 후 서버 재시작

### DB 툴 접속 정보가 필요한 경우 (MySQL)

- Host: `localhost`
- Port: `3306`
- Database: `squad`
- Username: `squad`
- Password: `squad`

root 계정:
- Username: `root`
- Password: `root`
