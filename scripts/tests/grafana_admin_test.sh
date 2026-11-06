#!/bin/bash
# scripts/ensure-grafana-admin-password.sh 테스트. docker 를 가짜 명령으로 바꿔 VM·Grafana 없이 돈다.
#   bash scripts/tests/grafana_admin_test.sh
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/ensure-grafana-admin-password.sh"
FAILS=0

# 가짜 docker: 인자는 docker.log 에, 표준입력(비밀번호)은 stdin.log 에 남긴다
setup() {
  T=$(mktemp -d)
  mkdir -p "$T/bin"
  cat > "$T/bin/docker" <<'FAKE'
#!/bin/bash
echo "$*" >> "$FAKE_DIR/docker.log"
cat > "$FAKE_DIR/stdin.log"
exit "${FAKE_RC:-0}"
FAKE
  chmod +x "$T/bin/docker"
  : > "$T/docker.log"
}

run() {
  set +e
  PATH="$T/bin:$PATH" FAKE_DIR="$T" bash "$SCRIPT" > "$T/out.log" 2>&1
  RC=$?
  set -e
}

check() {
  if eval "$2"; then echo "  ok: $1"; else echo "  FAIL: $1"; FAILS=$((FAILS + 1)); sed 's/^/    | /' "$T/out.log" "$T/docker.log"; fi
}

echo "[미설정] 아무것도 하지 않는다"
setup; unset GRAFANA_ADMIN_PASSWORD; run
check "성공" '[ "$RC" = 0 ]'
check "docker 호출 안 함" '[ ! -s "$T/docker.log" ]'

echo "[짧은 값] 적용하지 않고 실패한다"
setup; GRAFANA_ADMIN_PASSWORD="short" run
check "실패" '[ "$RC" != 0 ]'
check "docker 호출 안 함" '[ ! -s "$T/docker.log" ]'

echo "[정상] 표준입력으로만 넘긴다"
setup; GRAFANA_ADMIN_PASSWORD="correct-horse-battery" run
check "성공" '[ "$RC" = 0 ]'
check "grafana 컨테이너에서 stdin 옵션으로 재설정" 'grep -q "^exec -i grafana grafana cli admin reset-admin-password --password-from-stdin$" "$T/docker.log"'
check "비밀번호가 인자에 없음" '! grep -q "correct-horse-battery" "$T/docker.log"'
check "비밀번호가 출력에 없음" '! grep -q "correct-horse-battery" "$T/out.log"'
check "표준입력으로 전달" '[ "$(cat "$T/stdin.log")" = "correct-horse-battery" ]'

echo "[재설정 실패] 실패로 끝난다"
setup; GRAFANA_ADMIN_PASSWORD="correct-horse-battery" FAKE_RC=1 run
check "실패" '[ "$RC" != 0 ]'

if [ "$FAILS" -ne 0 ]; then echo "실패 $FAILS 건"; exit 1; fi
echo "모두 통과"
