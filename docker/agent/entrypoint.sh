#!/usr/bin/env sh
set -eu

if [ ! -f /app/agent-runner.jar ]; then
  echo "[squad-agent] agent-runner.jar not found at /app/agent-runner.jar" >&2
  exit 1
fi

exec java -jar /app/agent-runner.jar
