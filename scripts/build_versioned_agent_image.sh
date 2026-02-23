#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TAG="${1:-build-$(git rev-parse --short HEAD)}"
IMAGE="squad-agent:${TAG}"

echo "==> Building $IMAGE"
docker build --no-cache -f docker/agent/Dockerfile -t "$IMAGE" .

echo
echo "Built image: $IMAGE"
echo "Run app with:"
echo "SQUAD_AGENT_IMAGE=$IMAGE ./gradlew bootRun --args='--spring.profiles.active=local'"
