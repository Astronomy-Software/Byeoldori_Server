#!/bin/bash
# scripts/ensure-mysql-ft-stopword-off.sh 테스트. docker 를 가짜 명령으로 바꿔 VM·MySQL 없이 돈다.
#   bash scripts/tests/ft_stopword_test.sh
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/ensure-mysql-ft-stopword-off.sh"
FAILS=0

# 가짜 docker: $T/state 에 GLOBAL 값을 두고 SELECT 는 그 값을, SET PERSIST 는 FAKE_SET_RESULT(기본 0)로 바꾼다
setup() {
  T=$(mktemp -d)
  mkdir -p "$T/bin"
  echo "$1" > "$T/state"
  cat > "$T/bin/docker" <<'FAKE'
#!/bin/bash
echo "$*" >> "$FAKE_DIR/docker.log"
sql="${!#}"
case "$sql" in
  "SELECT @@GLOBAL.innodb_ft_enable_stopword") cat "$FAKE_DIR/state" ;;
  "SET PERSIST innodb_ft_enable_stopword = OFF") echo "${FAKE_SET_RESULT:-0}" > "$FAKE_DIR/state" ;;
  *) exit 2 ;;
esac
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

echo "[이미 OFF] 아무것도 바꾸지 않는다"
setup 0; run
check "성공" '[ "$RC" = 0 ]'
check "SET PERSIST 안 함" '! grep -q "SET PERSIST" "$T/docker.log"'
check "mysql 컨테이너에서 실행" 'grep -q "^exec mysql " "$T/docker.log"'

echo "[ON] SET PERSIST 로 끄고 다시 확인한다"
setup 1; run
check "성공" '[ "$RC" = 0 ]'
check "SET PERSIST 실행" 'grep -q "SET PERSIST innodb_ft_enable_stopword = OFF" "$T/docker.log"'
check "값이 0" '[ "$(cat "$T/state")" = 0 ]'

echo "[설정 실패] 값이 그대로면 실패로 끝나 배포를 막는다"
setup 1; FAKE_SET_RESULT=1 run
check "실패" '[ "$RC" != 0 ]'

if [ "$FAILS" -ne 0 ]; then echo "실패 $FAILS 건"; exit 1; fi
echo "모두 통과"
