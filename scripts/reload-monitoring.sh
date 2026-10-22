#!/bin/bash
# 모니터링 스택(prometheus·grafana·loki·alloy·cloudflare-exporter) 반영. 배포(deploy.yml)가 블루그린 전환 뒤 호출한다.
#   bash scripts/reload-monitoring.sh   (DEPLOY_DIR 기준, 실패해도 배포는 계속하도록 호출부에서 || true)
# compose 의 `up -d` 는 서비스 정의(이미지·env·마운트 경로)가 바뀔 때만 컨테이너를 다시 만들고, 바인드 마운트한 설정 파일의
# '내용'은 보지 않는다. 그래서 prometheus.yml·loki.yml·config.alloy·cf-worker-exporter.py 를 고쳐도 운영에 반영되지 않았다
# (exporter 는 기동 때 읽은 코드로 계속 돈다). 배포의 tar 압축 해제는 파일을 새로 만들어(새 inode) 단일 파일 바인드 마운트는
# 재시작만으로는 옛 내용을 볼 수 있으므로, 서비스별 마운트 파일의 sha256 을 monitoring/.<서비스>.sha256 에 기록해 두고
# 달라진 서비스만 --force-recreate 로 다시 만든다. compose 호출마다 timeout 으로 상한을 둔다.
set -uo pipefail
cd "${DEPLOY_DIR:-$HOME/byeoldori-server}" || exit 1
COMPOSE=(docker compose -f docker-compose.oci.yml -f docker-compose.monitoring.yml)
LIMIT="${MON_COMPOSE_TIMEOUT:-180}"
SERVICES="prometheus grafana loki alloy cloudflare-exporter"

# 서비스가 읽는 설정 파일. grafana 는 provisioning(알림 룰·데이터소스)을 기동 때만 읽는다.
# 대시보드 JSON 은 provider 가 주기적으로 다시 읽으므로 넣지 않는다.
config_files() {
  case "$1" in
    prometheus) echo monitoring/prometheus.yml ;;
    grafana) find monitoring/grafana/provisioning -type f | LC_ALL=C sort ;;
    loki) echo monitoring/loki.yml ;;
    alloy) echo monitoring/config.alloy ;;
    cloudflare-exporter) echo monitoring/cf-worker-exporter.py ;;
  esac
}

config_sum() {
  local files
  files=$(config_files "$1")
  [ -n "$files" ] || return 1
  # 파일 이름과 내용을 함께 해시한다(파일 추가·삭제도 변경으로 본다)
  # shellcheck disable=SC2086
  sha256sum $files | sha256sum | cut -d' ' -f1
}

FAILED=0
# 1) 없거나 정의가 바뀐 컨테이너를 만든다(정의가 같으면 no-op)
timeout "$LIMIT" "${COMPOSE[@]}" up -d $SERVICES || { echo "[monitoring] up -d 실패"; FAILED=1; }

# 2) 설정 파일 내용이 바뀐 서비스만 다시 만든다. 실패하면 기록을 갱신하지 않아 다음 배포에서 다시 시도한다.
for svc in $SERVICES; do
  sum=$(config_sum "$svc") || { echo "[monitoring] $svc 설정 파일을 찾지 못함"; FAILED=1; continue; }
  rec="monitoring/.$svc.sha256"
  [ "$(cat "$rec" 2>/dev/null)" = "$sum" ] && continue
  if timeout "$LIMIT" "${COMPOSE[@]}" up -d --no-deps --force-recreate "$svc"; then
    echo "$sum" > "$rec"
    echo "[monitoring] $svc 설정 변경 반영(재생성)"
  else
    echo "[monitoring] $svc 재생성 실패 — 다음 배포에서 다시 시도"
    FAILED=1
  fi
done
# 예전 배포가 쓰던 알림 룰 기록(grafana 는 이제 .grafana.sha256 로 관리)
rm -f monitoring/.alerting.sha256
exit "$FAILED"
