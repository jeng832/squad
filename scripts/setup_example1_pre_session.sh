#!/usr/bin/env bash
set -euo pipefail

# 예시 1: 번역-요약 Squad 생성 (세션 시작 전 단계까지)
# docs/EXAMPLES.md 기준

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=setup_examples_lib.sh
source "$SCRIPT_DIR/setup_examples_lib.sh"

orchestrator_id="$(ensure_agent \
  "번역-요약 오케스트레이터" \
  "ORCHESTRATOR" \
  "사용자의 한글 텍스트를 받아 1) 영어 번역기에게 번역 요청 2) 영어 요약기에게 요약 요청 3) 결과를 합쳐 최종 반환한다.")"

translator_id="$(ensure_agent \
  "영어 번역기" \
  "WORKER" \
  "한글 텍스트를 자연스러운 영어로 번역하고 번역 결과만 반환한다.")"

summarizer_id="$(ensure_agent \
  "영어 요약기" \
  "WORKER" \
  "영어 텍스트를 핵심 위주 3~5문장으로 요약하고 요약 결과만 반환한다.")"

agent_ids_json="[$translator_id,$summarizer_id]"

squad_id="$(ensure_squad \
  "번역-요약 Squad" \
  "한글을 영어로 번역하고 요약하는 팀" \
  "$orchestrator_id" \
  "$agent_ids_json")"

print_summary "Example 1 (번역-요약 Squad)" "$squad_id"
