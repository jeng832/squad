#!/usr/bin/env bash
set -euo pipefail

# 예시 2: Squad 코드 분석 응답 Squad 생성 (세션 시작 전 단계까지)
# docs/EXAMPLES.md 기준

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=setup_examples_lib.sh
source "$SCRIPT_DIR/setup_examples_lib.sh"

orchestrator_id="$(ensure_agent \
  "코드 리뷰 오케스트레이터" \
  "ORCHESTRATOR" \
  "요청을 분석 단위로 분해하고 적절한 에이전트에게 작업을 위임한다. 최종 응답은 근거 파일 경로와 함께 작성한다.")"

backend_id="$(ensure_agent \
  "백엔드 분석가" \
  "WORKER" \
  "Java/Spring 코드 중심으로 구조, 의존성, 잠재 결함, 테스트 공백을 분석한다. file_search, file_read, bash_exec를 활용한다.")"

infra_id="$(ensure_agent \
  "인프라 분석가" \
  "WORKER" \
  "Dockerfile, compose, 실행/배포 흐름, 운영 리스크를 분석한다. 필요한 경우 설정값과 포트를 명시한다.")"

scribe_id="$(ensure_agent \
  "문서화 담당" \
  "WORKER" \
  "수집된 분석을 1) 핵심 이슈 2) 영향도 3) 다음 액션 순서로 간결하게 정리한다.")"

agent_ids_json="[$backend_id,$infra_id,$scribe_id]"

squad_id="$(ensure_squad \
  "Squad 코드 분석 Squad" \
  "squad 레포 구조 분석 및 개선 제안" \
  "$orchestrator_id" \
  "$agent_ids_json")"

print_summary "Example 2 (Squad 코드 분석 Squad)" "$squad_id"
