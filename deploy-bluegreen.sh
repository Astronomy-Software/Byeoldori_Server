#!/bin/bash
set -euo pipefail
# ssh-action 은 PTY 없이 접속하므로 Actions 취소로 SSH 가 끊겨도 SIGHUP 이 오지 않는다. 대신 다음 출력이 닫힌 파이프에
# 쓰이며 SIGPIPE 로 즉사해 아래 EXIT trap(새 색 정지)이 돌지 않았다. PIPE 를 무시하면 출력 명령이 실패해 set -e 로
# trap 을 거쳐 끝난다(trap 안의 echo 는 출력 실패에도 정리를 마치도록 || true).
trap '' PIPE
cd "${DEPLOY_DIR:-$HOME/byeoldori-server}"
# 경로는 테스트(scripts/tests/bluegreen_test.sh)에서만 바꾼다.
UPSTREAM_CONF="${BG_UPSTREAM_CONF:-/etc/nginx/conf.d/byeoldori-upstream.conf}"
ACTIVE_FILE="${BG_ACTIVE_FILE:-/etc/nginx/byeoldori_active}"
# 서비스 중인 색은 nginx 가 실제로 읽는 upstream 파일의 포트로 정한다. active 파일은 전환 중 실패·중단으로
# upstream 과 어긋날 수 있고, 어긋난 채 다음 배포가 돌면 트래픽을 받는 색을 지운다. 포트를 못 읽을 때만 active 파일을 쓴다.
case "$(grep -oE '127\.0\.0\.1:808[01]' "$UPSTREAM_CONF" 2>/dev/null | head -1)" in
  *:8080) ACTIVE=blue ;;
  *:8081) ACTIVE=green ;;
  *) ACTIVE=$(cat "$ACTIVE_FILE" 2>/dev/null || echo none) ;;
esac
RECORDED=$(cat "$ACTIVE_FILE" 2>/dev/null || echo none)
[ "$RECORDED" = "$ACTIVE" ] || echo "[BG] 경고: active 파일($RECORDED)이 upstream($ACTIVE)과 달라 upstream 을 따른다"
if [ "$ACTIVE" = "blue" ]; then NEW=green; PORT=8081; else NEW=blue; PORT=8080; fi
OLD=$ACTIVE
echo "[BG] active=$ACTIVE → deploy=$NEW(:$PORT)"

# 전환(nginx upstream 변경) 전에 실패하거나 중단(SSH 끊김·Actions 취소)되면 새 색을 내린다.
# restart 정책 때문에 그냥 두면 크래시 루프를 돌거나, nginx 가 안 가리키는 채로 살아서
# @Scheduled 예보 수집을 서비스 중인 색과 중복 실행한다. 컨테이너는 지우지 않고 정지만 해
# VM 에서 `docker logs app-$NEW` 로 원인을 볼 수 있게 남긴다(다음 배포 때 rm -f 로 정리됨).
# upstream 파일을 바꾼 뒤 reload 성공 전에 실패·중단되면(SWITCHING=1) 백업해 둔 upstream 을 되돌리고 reload 한다.
# 새 파일을 남겨 두면 운영자가 nginx 를 고쳐 reload 하거나 재부팅할 때 nginx 가 새 색으로 넘어간다.
# 이때 두 색은 모두 살아 있으므로 새 색은 내리지 않는다.
SWITCHING=0
cleanup_new() {
  local rc=$?
  if [ "$rc" -ne 0 ] && [ "$SWITCHING" = "0" ]; then
    echo "[BG] 실패(rc=$rc) — app-$NEW 정지(기존 $OLD 유지). 원인: docker logs app-$NEW" || true
    docker update --restart=no app-$NEW >/dev/null 2>&1 || true
    docker stop app-$NEW >/dev/null 2>&1 || true
  elif [ "$rc" -ne 0 ] && [ "$SWITCHING" = "1" ]; then
    if [ -f "$UPSTREAM_CONF.bak" ]; then
      echo "[BG] 전환 실패(rc=$rc) — upstream 을 $OLD 로 되돌림" || true
      sudo cp -p "$UPSTREAM_CONF.bak" "$UPSTREAM_CONF" || true
      if sudo nginx -t; then sudo systemctl reload nginx || true; fi
    else
      echo "[BG] 전환 실패(rc=$rc) — 되돌릴 upstream 백업 없음. nginx 설정 확인 필요" || true
    fi
  fi
}
trap cleanup_new EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

docker compose -f docker-compose.oci.yml -f docker-compose.bluegreen.yml build app-$NEW
docker rm -f app-$NEW 2>/dev/null || true
[ "$NEW" = "blue" ] && docker rm -f app 2>/dev/null || true   # 구 단일컨테이너(8080) 정리
docker compose -f docker-compose.oci.yml -f docker-compose.bluegreen.yml up -d --no-deps app-$NEW
# arm64 느린 기동 대비 6분(72×5s) 게이트. mail 은 health 에서 제외되어 있으므로
# 이 UP 은 실제 서비스 준비 완료를 뜻한다.
for i in $(seq 1 72); do
  if curl -sf http://127.0.0.1:$PORT/actuator/health | grep -q "\"status\":\"UP\""; then OK=1; break; fi
  sleep 5
done
[ "${OK:-}" = "1" ] || { echo "[BG] health FAIL — 전환 안 함(기존 유지)"; exit 1; }
# 초단기·단기 예보는 컨테이너 메모리에만 있어 새 색은 기동 직후 비어 있다. 바로 전환하고 구 색을 내리면
# 첫 적재(기상청 호출 수백 번)가 끝날 때까지 예보가 빈 채로 서비스된다. warm 그룹이 UP(적재 완료)이
# 될 때까지 기다리되, 기상청 장애로 적재가 안 끝나도 배포가 막히지 않게 상한(기본 5분=60×5s)이 지나면 경고만 남기고 전환한다.
# 서비스 중인 색이 없으면(첫 배포) 기다릴 이유가 없다.
if [ "$OLD" != "none" ]; then
  WARM=0
  for i in $(seq 1 "${BG_WARM_TRIES:-60}"); do
    if curl -sf http://127.0.0.1:$PORT/actuator/health/warm | grep -q "\"status\":\"UP\""; then WARM=1; break; fi
    sleep 5
  done
  [ "$WARM" = "1" ] && echo "[BG] 예보 캐시 적재 확인" \
    || echo "[BG] 경고: 예보 캐시 적재를 상한 안에 확인 못 함 — 그대로 전환(적재되면 자동 복구). 원인: docker logs app-$NEW"
fi
# 여기부터는 nginx 설정을 건드리므로 새 색을 자동으로 내리지 않는다(내리면 upstream 이 죽은 포트를 가리킬 수 있음).
sudo rm -f "$UPSTREAM_CONF.bak"
[ -f "$UPSTREAM_CONF" ] && sudo cp -p "$UPSTREAM_CONF" "$UPSTREAM_CONF.bak"
SWITCHING=1
echo "upstream byeoldori_backend { server 127.0.0.1:$PORT; }" | sudo tee "$UPSTREAM_CONF" >/dev/null
# `a && b` 는 a 가 실패해도 set -e 로 멈추지 않아 reload 없이 구 색을 정지하던 문제 → 각각 실패 시 중단.
# 중단되면 trap 이 upstream 을 되돌리고, 두 색이 모두 살아 있으므로 서비스는 유지된다.
sudo nginx -t
sudo systemctl reload nginx
SWITCHING=2
echo $NEW | sudo tee "$ACTIVE_FILE" >/dev/null
# 이전 배포(restart: always 시절)에 만들어진 구 색은 정책을 먼저 바꿔야 재부팅 후 되살아나지 않는다.
# reload 직후엔 nginx 구 워커가 아직 구 색으로 요청을 보낼 수 있어 잠깐 기다린 뒤 내린다.
# 정지는 앱의 graceful shutdown(30초)보다 길게 기다린다(-t 35). 기본 10초면 처리 중이던 긴 요청이 SIGKILL 로 끊긴다.
if [ "$OLD" != "none" ]; then
  docker update --restart=unless-stopped app-$OLD >/dev/null 2>&1 || true
  sleep 2
  docker stop -t 35 app-$OLD 2>/dev/null || true
fi
docker rm -f app 2>/dev/null || true
echo "[BG] switched → $NEW"
