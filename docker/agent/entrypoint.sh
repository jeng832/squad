#!/usr/bin/env sh
set -eu

if [ ! -f /app/agent-runner.jar ]; then
  echo "[squad-agent] agent-runner.jar not found at /app/agent-runner.jar" >&2
  exit 1
fi

if [ -n "${GIT_CLONE_URL:-}" ]; then
  GIT_BRANCH="${GIT_BRANCH:-main}"
  echo "[squad-agent] Cloning repository (branch: ${GIT_BRANCH})..."
  git clone --depth 1 --branch "${GIT_BRANCH}" "${GIT_CLONE_URL}" /workspace
  echo "[squad-agent] Clone complete"
else
  mkdir -p /workspace
fi

exec java -jar /app/agent-runner.jar
