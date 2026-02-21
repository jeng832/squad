#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TAG="build-$(git rev-parse --short HEAD)"
IMAGE="squad-agent:${TAG}"

echo "==> Using agent image: $IMAGE"
SQUAD_AGENT_IMAGE="$IMAGE" ./scripts/reset_local_environment.sh

echo
echo "==> Starting app with image: $IMAGE"
SQUAD_AGENT_IMAGE="$IMAGE" ./gradlew bootRun --args='--spring.profiles.active=local'
