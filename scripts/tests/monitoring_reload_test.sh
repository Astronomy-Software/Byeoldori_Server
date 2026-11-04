#!/bin/bash
# scripts/reload-monitoring.sh 테스트. docker 를 가짜 명령으로 바꿔 VM 없이 돈다.
#   bash scripts/tests/monitoring_reload_test.sh
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/reload-monitoring.sh"
FAILS=0

setup() {
  T=$(mktemp -d)
  mkdir -p "$T/bin" "$T/deploy/monitoring/grafana/provisioning/alerting" "$T/deploy/monitoring/grafana/provisioning/datasources"
  local m="$T/deploy/monitoring"
  echo "scrape" > "$m/prometheus.yml"
  echo "loki" > "$m/loki.yml"
  echo "alloy" > "$m/config.alloy"
  echo "print('v1')" > "$m/cf-worker-exporter.py"
  echo "rules: v1" > "$m/grafana/provisioning/alerting/alerting.yml"
  echo "ds" > "$m/grafana/provisioning/datasources/datasources.yml"
  echo "old" > "$m/.alerting.sha256"
  # FAKE_RECREATE_FAIL 에 지정한 서비스의 재생성만 실패시킨다
  cat > "$T/bin/docker" <<'EOF'
#!/bin/bash
echo "$*" >> "$FAKE_LOG/docker.log"
case "$*" in *"--force-recreate ${FAKE_RECREATE_FAIL:-none}") exit 1 ;; esac
exit 0
EOF
  chmod +x "$T/bin/"*
}

run_reload() {
  : > "$T/docker.log"
  set +e
  PATH="$T/bin:$PATH" FAKE_LOG="$T" DEPLOY_DIR="$T/deploy" bash "$SCRIPT" > "$T/out.log" 2>&1
  RC=$?
  set -e
}

recreated() { grep -oE -- "--force-recreate [a-z-]+" "$T/docker.log" | awk '{print $2}' | sort | tr '\n' ' ' | sed 's/ $//'; }

check() {
  if eval "$2"; then echo "  ok: $1"; else echo "  FAIL: $1"; FAILS=$((FAILS + 1)); sed 's/^/    | /' "$T/out.log" "$T/docker.log"; fi
}

echo "[1] 첫 실행(기록 없음): 전부 띄우고 다섯 서비스 모두 재생성해 현재 설정을 반영한다"
setup
run_reload
check "성공 코드" '[ "$RC" -eq 0 ]'
check "up -d 로 다섯 서비스를 띄움" 'grep -q "up -d prometheus grafana loki alloy cloudflare-exporter$" "$T/docker.log"'
check "다섯 서비스 재생성" '[ "$(recreated)" = "alloy cloudflare-exporter grafana loki prometheus" ]'
check "서비스별 기록 생성" '[ -s "$T/deploy/monitoring/.cloudflare-exporter.sha256" ] && [ -s "$T/deploy/monitoring/.grafana.sha256" ]'
check "예전 알림 룰 기록 삭제" '[ ! -e "$T/deploy/monitoring/.alerting.sha256" ]'

echo "[2] 설정이 그대로면 재생성하지 않는다"
run_reload
check "성공 코드" '[ "$RC" -eq 0 ]'
check "재생성 없음" '[ -z "$(recreated)" ]'

echo "[3] exporter 코드만 바뀌면 exporter 만 재생성한다(파일을 새로 써도 내용 기준)"
echo "print('v2')" > "$T/deploy/monitoring/cf-worker-exporter.py"
run_reload
check "cloudflare-exporter 만 재생성" '[ "$(recreated)" = "cloudflare-exporter" ]'

echo "[4] grafana provisioning(알림 룰) 이 바뀌면 grafana 만 재생성한다"
echo "rules: v2" > "$T/deploy/monitoring/grafana/provisioning/alerting/alerting.yml"
run_reload
check "grafana 만 재생성" '[ "$(recreated)" = "grafana" ]'

echo "[5] 재생성이 실패하면 기록을 남기지 않아 다음 배포에서 다시 시도하고, 다른 서비스는 계속 처리한다"
echo "scrape v2" > "$T/deploy/monitoring/prometheus.yml"
echo "loki v2" > "$T/deploy/monitoring/loki.yml"
FAKE_RECREATE_FAIL=prometheus run_reload
check "실패 코드(호출부는 || true 로 배포 계속)" '[ "$RC" -ne 0 ]'
check "loki 는 재생성" 'grep -q -- "--force-recreate loki" "$T/docker.log"'
run_reload
check "다음 실행에서 prometheus 재시도" '[ "$(recreated)" = "prometheus" ]'
check "재시도 성공" '[ "$RC" -eq 0 ]'
rm -rf "$T"

[ "$FAILS" -eq 0 ] && echo "monitoring_reload_test: 모두 통과" || { echo "monitoring_reload_test: 실패 $FAILS 건"; exit 1; }
