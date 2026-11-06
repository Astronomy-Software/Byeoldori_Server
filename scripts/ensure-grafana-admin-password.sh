#!/bin/bash
# Grafana 관리자(admin) 비밀번호를 GRAFANA_ADMIN_PASSWORD 값으로 맞춘다(배포마다 호출, 멱등).
# GF_SECURITY_ADMIN_PASSWORD 는 grafana_data 를 처음 만들 때만 쓰여서, 이미 떠 있는 Grafana 의 비밀번호는 바꾸지 못한다.
# 그래서 grafana cli 로 직접 재설정한다. 값이 비어 있으면 아무것도 하지 않는다(GitHub secret 등록 전에는 현 상태 유지).
# 비밀번호는 표준입력으로만 넘겨 호스트 프로세스 목록·로그에 남지 않는다.
# 배포(.github/workflows/deploy.yml)가 모니터링 반영 뒤 호출한다. 수동: GRAFANA_ADMIN_PASSWORD=... bash scripts/ensure-grafana-admin-password.sh
# 테스트(scripts/tests/grafana_admin_test.sh)는 PATH 의 가짜 docker 로 돈다.
set -euo pipefail
CONTAINER="${GRAFANA_CONTAINER:-grafana}"
MIN_LENGTH=12

if [ -z "${GRAFANA_ADMIN_PASSWORD:-}" ]; then
  echo "[grafana-admin] GRAFANA_ADMIN_PASSWORD 미설정 — 건너뜀"
  exit 0
fi
if [ "${#GRAFANA_ADMIN_PASSWORD}" -lt "$MIN_LENGTH" ]; then
  echo "[grafana-admin] 비밀번호가 ${MIN_LENGTH}자 미만이라 적용하지 않음" >&2
  exit 1
fi

printf '%s' "$GRAFANA_ADMIN_PASSWORD" \
  | docker exec -i "$CONTAINER" grafana cli admin reset-admin-password --password-from-stdin >/dev/null
echo "[grafana-admin] 관리자 비밀번호 적용"
