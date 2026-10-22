#!/bin/bash
# deploy-bluegreen.sh 전환 실패 처리 테스트. docker·sudo·nginx·systemctl·curl 을 가짜 명령으로 바꿔 VM 없이 돈다.
#   bash scripts/tests/bluegreen_test.sh
# nginx 경로는 BG_UPSTREAM_CONF·BG_ACTIVE_FILE 로 임시 폴더를 가리킨다.
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")/../.." && pwd)/deploy-bluegreen.sh"
FAILS=0

setup() {   # $1: upstream 포트(없으면 파일 없음), $2: active 파일 내용(없으면 파일 없음)
  T=$(mktemp -d)
  mkdir -p "$T/bin" "$T/deploy" "$T/nginx/conf.d"
  [ -n "${1:-}" ] && echo "upstream byeoldori_backend { server 127.0.0.1:$1; }" > "$T/nginx/conf.d/byeoldori-upstream.conf"
  [ -n "${2:-}" ] && echo "$2" > "$T/nginx/byeoldori_active"
  cat > "$T/bin/docker" <<'EOF'
#!/bin/bash
echo "$*" >> "$FAKE_LOG/docker.log"
EOF
  cat > "$T/bin/sudo" <<'EOF'
#!/bin/bash
exec "$@"
EOF
  cat > "$T/bin/nginx" <<'EOF'
#!/bin/bash
[ -n "${FAKE_NGINX_FAIL:-}" ] && exit 1
exit 0
EOF
  cat > "$T/bin/systemctl" <<'EOF'
#!/bin/bash
[ -n "${FAKE_RELOAD_FAIL:-}" ] && exit 1
echo "$*" >> "$FAKE_LOG/systemctl.log"
EOF
  cat > "$T/bin/curl" <<'EOF'
#!/bin/bash
[ -n "${FAKE_HEALTH_FAIL:-}" ] && exit 22
echo '{"status":"UP"}'
EOF
  printf '#!/bin/bash\nexit 0\n' > "$T/bin/sleep"
  chmod +x "$T/bin/"*
  : > "$T/docker.log"
}

run_bg() {
  set +e
  PATH="$T/bin:$PATH" FAKE_LOG="$T" DEPLOY_DIR="$T/deploy" \
    BG_UPSTREAM_CONF="$T/nginx/conf.d/byeoldori-upstream.conf" BG_ACTIVE_FILE="$T/nginx/byeoldori_active" \
    bash "$SCRIPT" > "$T/out.log" 2>&1
  RC=$?
  set -e
}

upstream_port() { grep -oE '808[01]' "$T/nginx/conf.d/byeoldori-upstream.conf"; }
active() { cat "$T/nginx/byeoldori_active"; }

check() {
  if eval "$2"; then echo "  ok: $1"; else echo "  FAIL: $1"; FAILS=$((FAILS + 1)); sed 's/^/    | /' "$T/out.log"; fi
}

echo "[1] 정상 전환: blue → green"
setup 8080 blue
run_bg
check "성공 코드" '[ "$RC" -eq 0 ]'
check "upstream 이 8081" '[ "$(upstream_port)" = "8081" ]'
check "active 가 green" '[ "$(active)" = "green" ]'
check "구 색(blue) 정지(graceful 30초보다 긴 35초 대기)" 'grep -q "^stop -t 35 app-blue" "$T/docker.log"'
rm -rf "$T"

echo "[2] nginx -t 실패: upstream 을 되돌리고 active 는 그대로, 두 색 모두 유지"
setup 8080 blue
FAKE_NGINX_FAIL=1 run_bg
check "실패 코드" '[ "$RC" -ne 0 ]'
check "upstream 이 8080 으로 복구" '[ "$(upstream_port)" = "8080" ]'
check "active 는 blue" '[ "$(active)" = "blue" ]'
check "어느 색도 정지하지 않음" '! grep -q "^stop " "$T/docker.log"'
echo "    → nginx 를 고친 뒤 다음 배포는 다시 green 에 배포하고 blue 를 지우지 않는다"
: > "$T/docker.log"
run_bg
check "성공 코드" '[ "$RC" -eq 0 ]'
check "서비스 중인 blue 를 지우지 않음" '! grep -q "^rm -f app-blue" "$T/docker.log"'
check "upstream 이 8081" '[ "$(upstream_port)" = "8081" ]'
rm -rf "$T"

echo "[3] reload 실패: upstream 을 되돌린다"
setup 8081 green
FAKE_RELOAD_FAIL=1 run_bg
check "실패 코드" '[ "$RC" -ne 0 ]'
check "upstream 이 8081 로 복구" '[ "$(upstream_port)" = "8081" ]'
check "active 는 green" '[ "$(active)" = "green" ]'
rm -rf "$T"

echo "[4] active 파일이 upstream 과 어긋나면(reload 직후 중단) upstream 을 따른다"
setup 8081 blue
run_bg
check "성공 코드" '[ "$RC" -eq 0 ]'
check "서비스 중인 green 을 지우지 않음" '! grep -q "^rm -f app-green" "$T/docker.log"'
check "blue 에 배포" '[ "$(upstream_port)" = "8080" ] && [ "$(active)" = "blue" ]'
rm -rf "$T"

echo "[5] 헬스 게이트 실패: 새 색만 정지, upstream 은 그대로"
setup 8080 blue
FAKE_HEALTH_FAIL=1 run_bg
check "실패 코드" '[ "$RC" -ne 0 ]'
check "새 색(green) 정지" 'grep -q "^stop app-green" "$T/docker.log"'
check "upstream 이 8080 유지" '[ "$(upstream_port)" = "8080" ]'
rm -rf "$T"

echo "[6] 첫 배포(nginx 파일 없음): blue 에 배포"
setup "" ""
run_bg
check "성공 코드" '[ "$RC" -eq 0 ]'
check "upstream 이 8080, active 가 blue" '[ "$(upstream_port)" = "8080" ] && [ "$(active)" = "blue" ]'
rm -rf "$T"

[ "$FAILS" -eq 0 ] && echo "bluegreen_test: 모두 통과" || { echo "bluegreen_test: 실패 $FAILS 건"; exit 1; }
