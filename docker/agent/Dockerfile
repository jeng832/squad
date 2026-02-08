FROM eclipse-temurin:21-jre

WORKDIR /app

COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Agent Runner JAR은 빌드 파이프라인에서 /app/agent-runner.jar로 복사됨을 전제로 합니다.

ENTRYPOINT ["/app/entrypoint.sh"]
