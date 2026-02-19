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

### DB 툴 접속 정보가 필요한 경우 (MySQL)

- Host: `localhost`
- Port: `3306`
- Database: `squad`
- Username: `squad`
- Password: `squad`

root 계정:
- Username: `root`
- Password: `root`
