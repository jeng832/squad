#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
AGENT_IMAGE="${SQUAD_AGENT_IMAGE:-squad-agent:latest}"

print_step() {
  printf '\n==> %s\n' "$1"
}

print_step "Stopping local infra (and removing volumes)"
docker compose down -v || true

print_step "Removing existing squad agent containers"
container_ids="$(docker ps -aq --filter "name=squad-")"
if [[ -n "${container_ids}" ]]; then
  # shellcheck disable=SC2086
  docker rm -f ${container_ids} || true
else
  echo "No squad-* containers to remove"
fi

print_step "Removing existing squad-agent image"
docker image rm -f "$AGENT_IMAGE" >/dev/null 2>&1 || true

print_step "Starting infra"
docker compose up -d

print_step "Rebuilding agent image (no cache)"
docker build --no-cache -f docker/agent/Dockerfile -t "$AGENT_IMAGE" .

print_step "Done"
echo "Agent image: $AGENT_IMAGE"
echo "Now restart app: SQUAD_AGENT_IMAGE=$AGENT_IMAGE ./gradlew bootRun --args='--spring.profiles.active=local'"
