#!/bin/bash
# scripts/db-upgrade.sh 의 mongo-ready 판정 테스트. docker 를 가짜 명령으로 바꿔 VM 없이 돈다.
#   bash scripts/tests/db_upgrade_test.sh
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/db-upgrade.sh"
FAILS=0

# 가짜 docker: FAKE_MONGO=1 이면 mongodb 컨테이너가 있고, FAKE_VOL=1 이면 새 볼륨이 있다
setup() {
  T=$(mktemp -d)
  mkdir -p "$T/bin" "$T/deploy"
  cat > "$T/bin/docker" <<'FAKE'
#!/bin/bash
echo "$*" >> "$FAKE_DIR/docker.log"
case "$1 $2" in
  "inspect mongodb") [ "${FAKE_MONGO:-0}" = 1 ] ;;
  "inspect -f") [ "${FAKE_MONGO:-0}" = 1 ] && echo "byeoldori-server" ;;
  "volume inspect") [ "${FAKE_VOL:-0}" = 1 ] && [ "$3" = "byeoldori-server_mongo_data_v8" ] ;;
  *) exit 2 ;;
esac
FAKE
  chmod +x "$T/bin/docker"
  : > "$T/docker.log"
}

run() {
  set +e
  PATH="$T/bin:$PATH" FAKE_DIR="$T" DEPLOY_DIR="$T/deploy" bash "$SCRIPT" mongo-ready > "$T/out.log" 2>&1
  RC=$?
  set -e
}

check() {
  if eval "$2"; then echo "  ok: $1"; else echo "  FAIL: $1"; FAILS=$((FAILS + 1)); sed 's/^/    | /' "$T/out.log" "$T/docker.log"; fi
}

echo "[새 VM] mongodb 컨테이너가 없으면 만들어도 된다"
setup; FAKE_MONGO=0 FAKE_VOL=0 run
check "0" '[ "$RC" = 0 ]'

echo "[준비 전] 컨테이너는 있는데 새 볼륨이 없으면 건드리지 않는다"
setup; FAKE_MONGO=1 FAKE_VOL=0 run
check "1" '[ "$RC" = 1 ]'
check "프로젝트 접두어 붙은 볼륨 이름으로 확인" 'grep -q "volume inspect byeoldori-server_mongo_data_v8" "$T/docker.log"'

echo "[준비 후] 새 볼륨이 있으면 갱신해도 된다"
setup; FAKE_MONGO=1 FAKE_VOL=1 run
check "0" '[ "$RC" = 0 ]'

echo "[잘못된 명령] 사용법을 알리고 2"
setup
set +e; PATH="$T/bin:$PATH" FAKE_DIR="$T" DEPLOY_DIR="$T/deploy" bash "$SCRIPT" nope > "$T/out.log" 2>&1; RC=$?; set -e
check "2" '[ "$RC" = 2 ]'

if [ "$FAILS" -ne 0 ]; then echo "실패 $FAILS 건"; exit 1; fi
echo "모두 통과"
