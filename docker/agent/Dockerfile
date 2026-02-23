FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /workspace

COPY build.gradle settings.gradle /workspace/
COPY gradle /workspace/gradle
COPY src /workspace/src

RUN gradle --no-daemon -PagentRunner=true bootJar

FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY docker/agent/entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

COPY --from=builder /workspace/build/libs/agent-runner.jar /app/agent-runner.jar

ENTRYPOINT ["/app/entrypoint.sh"]
