#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_BASE_URL="${SQUAD_API_BASE_URL:-http://localhost:8080/api/v1}"

# Agent llmConfig에 사용할 Secret 참조값 (ref:secret/<name> 형식)
SQUAD_CLAUDE_API_KEY_REF=""

find_secret_by_name() {
  local name="$1"
  api_get "/secrets" | python3 -c "
import json,sys
name=sys.argv[1]
raw=sys.stdin.read()
if not raw.strip():
    raise SystemExit('empty response body from /secrets')
obj=json.loads(raw)
for s in obj.get('data', []):
    if s.get('name') == name:
        print(json.dumps(s))
        break
" "$name"
}

setup_api_key_secret() {
  local secret_name
  echo -n "Secret 이름을 입력하세요 (기본값: claude-api-key): " >&2
  read -r secret_name || true
  echo >&2
  secret_name="${secret_name:-claude-api-key}"

  local found
  found="$(find_secret_by_name "$secret_name")"
  if [[ -n "$found" ]]; then
    echo "[INFO] '$secret_name' Secret이 이미 존재합니다. 기존 Secret을 사용합니다." >&2
    SQUAD_CLAUDE_API_KEY_REF="ref:secret/$secret_name"
    return
  fi

  local api_key
  echo -n "Claude API Key를 입력하세요 (입력값은 화면에 표시되지 않습니다): " >&2
  read -rs api_key || true
  echo >&2
  if [[ -z "${api_key:-}" ]]; then
    echo "[ERROR] Claude API Key가 입력되지 않았습니다." >&2
    exit 1
  fi

  local payload
  payload=$(cat <<JSON
{"name": "$secret_name", "value": "$api_key"}
JSON
)
  api_post "/secrets" "$payload" >/dev/null
  echo "[INFO] Secret '$secret_name'이 생성되었습니다." >&2
  SQUAD_CLAUDE_API_KEY_REF="ref:secret/$secret_name"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[ERROR] Required command not found: $1" >&2
    exit 1
  }
}

ensure_server_ready() {
  local status
  status="$(curl -s -o /dev/null -w "%{http_code}" "$API_BASE_URL/agents" || true)"
  if [[ "$status" != "200" ]]; then
    echo "[ERROR] Squad API is not ready: $API_BASE_URL (status=$status)" >&2
    echo "        Start server first: ./gradlew bootRun --args='--spring.profiles.active=local'" >&2
    exit 1
  fi
}

api_get() {
  local path="$1"
  curl -fsS "$API_BASE_URL$path"
}

api_post() {
  local path="$1"
  local payload="$2"
  curl -fsS -X POST "$API_BASE_URL$path" \
    -H 'Content-Type: application/json' \
    -d "$payload"
}

api_put() {
  local path="$1"
  local payload="$2"
  curl -fsS -X PUT "$API_BASE_URL$path" \
    -H 'Content-Type: application/json' \
    -d "$payload"
}

json_extract() {
  local expr="$1"
  python3 -c "
import json,sys
raw=sys.stdin.read()
if not raw.strip():
    raise SystemExit('empty response body')
d=json.loads(raw)
print($expr)
"
}

find_agent_by_name() {
  local name="$1"
  api_get "/agents" | python3 -c "
import json,sys
name=sys.argv[1]
raw=sys.stdin.read()
if not raw.strip():
    raise SystemExit('empty response body from /agents')
obj=json.loads(raw)
for a in obj.get('data', []):
    if a.get('name') == name:
        print(json.dumps(a))
        break
" "$name"
}

create_agent() {
  local name="$1"
  local role_type="$2"
  local role_prompt="$3"
  local model="${4:-claude-sonnet-4-20250514}"

  local payload
  payload=$(cat <<JSON
{
  "name": "$name",
  "roleType": "$role_type",
  "role": "$role_prompt",
  "llmConfig": {
    "provider": "claude",
    "model": "$model",
    "apiKey": "$SQUAD_CLAUDE_API_KEY_REF"
  }
}
JSON
)

  local res
  res="$(api_post "/agents" "$payload")"
  echo "$res"
}

ensure_agent() {
  local name="$1"
  local role_type="$2"
  local role_prompt="$3"

  local found
  found="$(find_agent_by_name "$name")"
  if [[ -n "$found" ]]; then
    local id current_role_type
    id="$(echo "$found" | json_extract 'd["id"]')"
    current_role_type="$(echo "$found" | json_extract 'd["roleType"]')"
    if [[ "$current_role_type" != "$role_type" ]]; then
      echo "[ERROR] Agent '$name' exists with roleType=$current_role_type (expected $role_type)" >&2
      exit 1
    fi
    echo "$id"
    return
  fi

  local created
  created="$(create_agent "$name" "$role_type" "$role_prompt")"
  echo "$created" | json_extract 'd["data"]["id"]'
}

find_squad_by_name() {
  local name="$1"
  api_get "/squads" | python3 -c "
import json,sys
name=sys.argv[1]
raw=sys.stdin.read()
if not raw.strip():
    raise SystemExit('empty response body from /squads')
obj=json.loads(raw)
for s in obj.get('data', []):
    if s.get('name') == name:
        print(json.dumps(s))
        break
" "$name"
}

ensure_squad() {
  local name="$1"
  local description="$2"
  local orchestrator_id="$3"
  local agent_ids_json="$4"

  local found
  found="$(find_squad_by_name "$name")"

  local payload
  payload=$(cat <<JSON
{
  "name": "$name",
  "description": "$description",
  "orchestratorId": $orchestrator_id,
  "agentIds": $agent_ids_json,
  "directCommunication": null
}
JSON
)

  if [[ -n "$found" ]]; then
    local squad_id
    squad_id="$(echo "$found" | json_extract 'd["id"]')"
    # orchestratorId는 update에서 바꿀 수 없으므로 mismatch면 실패 처리
    local current_orch
    current_orch="$(echo "$found" | json_extract 'd["orchestratorId"]')"
    if [[ "$current_orch" != "$orchestrator_id" ]]; then
      echo "[ERROR] Squad '$name' already exists with orchestratorId=$current_orch (expected $orchestrator_id)" >&2
      exit 1
    fi

    local update_payload
    update_payload=$(cat <<JSON
{
  "name": "$name",
  "description": "$description",
  "agentIds": $agent_ids_json,
  "directCommunication": null
}
JSON
)
    api_put "/squads/$squad_id" "$update_payload" >/dev/null
    echo "$squad_id"
    return
  fi

  local created
  created="$(api_post "/squads" "$payload")"
  echo "$created" | json_extract 'd["data"]["id"]'
}

print_summary() {
  local title="$1"
  local squad_id="$2"
  echo
  echo "[OK] $title setup completed"
  echo "- Squad ID: $squad_id"
  echo "- Next: create/start session with this squad"
}

require_command curl
require_command python3
ensure_server_ready
setup_api_key_secret
