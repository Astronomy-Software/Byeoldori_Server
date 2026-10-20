#!/bin/bash
set -euo pipefail
cd ~/byeoldori-server
ACTIVE=$(cat /etc/nginx/byeoldori_active 2>/dev/null || echo none)
if [ "$ACTIVE" = "blue" ]; then NEW=green; PORT=8081; else NEW=blue; PORT=8080; fi
OLD=$ACTIVE
echo "[BG] active=$ACTIVE → deploy=$NEW(:$PORT)"

# 전환(nginx upstream 변경) 전에 실패하거나 중단(SSH 끊김·Actions 취소)되면 새 색을 내린다.
# restart 정책 때문에 그냥 두면 크래시 루프를 돌거나, nginx 가 안 가리키는 채로 살아서
# @Scheduled 예보 수집을 서비스 중인 색과 중복 실행한다. 컨테이너는 지우지 않고 정지만 해
# VM 에서 `docker logs app-$NEW` 로 원인을 볼 수 있게 남긴다(다음 배포 때 rm -f 로 정리됨).
SWITCHING=0
cleanup_new() {
  local rc=$?
  if [ "$rc" -ne 0 ] && [ "$SWITCHING" = "0" ]; then
    echo "[BG] 실패(rc=$rc) — app-$NEW 정지(기존 $OLD 유지). 원인: docker logs app-$NEW"
    docker update --restart=no app-$NEW >/dev/null 2>&1 || true
    docker stop app-$NEW >/dev/null 2>&1 || true
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
# 여기부터는 nginx 설정을 건드리므로 새 색을 자동으로 내리지 않는다(내리면 upstream 이 죽은 포트를 가리킬 수 있음).
SWITCHING=1
echo "upstream byeoldori_backend { server 127.0.0.1:$PORT; }" | sudo tee /etc/nginx/conf.d/byeoldori-upstream.conf >/dev/null
# `a && b` 는 a 가 실패해도 set -e 로 멈추지 않아 reload 없이 구 색을 정지하던 문제 → 각각 실패 시 중단.
# 중단되면 두 색이 모두 살아 있으므로 nginx 가 어느 쪽을 가리켜도 서비스는 유지된다.
sudo nginx -t
sudo systemctl reload nginx
echo $NEW | sudo tee /etc/nginx/byeoldori_active >/dev/null
# 이전 배포(restart: always 시절)에 만들어진 구 색은 정책을 먼저 바꿔야 재부팅 후 되살아나지 않는다.
if [ "$OLD" != "none" ]; then
  docker update --restart=unless-stopped app-$OLD >/dev/null 2>&1 || true
  docker stop app-$OLD 2>/dev/null || true
fi
docker rm -f app 2>/dev/null || true
echo "[BG] switched → $NEW"
