#!/bin/bash
set -euo pipefail
cd ~/byeoldori-server
ACTIVE=$(cat /etc/nginx/byeoldori_active 2>/dev/null || echo none)
if [ "$ACTIVE" = "blue" ]; then NEW=green; PORT=8081; else NEW=blue; PORT=8080; fi
OLD=$ACTIVE
echo "[BG] active=$ACTIVE → deploy=$NEW(:$PORT)"
docker compose -f docker-compose.oci.yml -f docker-compose.bluegreen.yml build app-$NEW
docker rm -f app-$NEW 2>/dev/null || true
[ "$NEW" = "blue" ] && docker rm -f app 2>/dev/null || true   # 구 단일컨테이너(8080) 정리
docker compose -f docker-compose.oci.yml -f docker-compose.bluegreen.yml up -d --no-deps app-$NEW
for i in $(seq 1 36); do
  if curl -sf http://127.0.0.1:$PORT/actuator/health | grep -q "\"status\":\"UP\""; then OK=1; break; fi
  sleep 5
done
[ "${OK:-}" = "1" ] || { echo "[BG] health FAIL — 전환 안 함(기존 유지)"; exit 1; }
echo "upstream byeoldori_backend { server 127.0.0.1:$PORT; }" | sudo tee /etc/nginx/conf.d/byeoldori-upstream.conf >/dev/null
sudo nginx -t && sudo systemctl reload nginx
echo $NEW | sudo tee /etc/nginx/byeoldori_active >/dev/null
[ "$OLD" != "none" ] && docker stop app-$OLD 2>/dev/null || true
docker rm -f app 2>/dev/null || true
echo "[BG] switched → $NEW"
