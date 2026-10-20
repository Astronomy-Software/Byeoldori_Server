#!/bin/bash
# scripts/backup.sh 를 매일 한 번 돌리는 crontab 항목을 (재)설치한다. 여러 번 실행해도 항목은 하나만 남는다.
# 배포(.github/workflows/deploy.yml)가 매번 호출한다. 수동: bash ~/byeoldori-server/scripts/install-backup-cron.sh
set -euo pipefail
DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_ROOT="${BACKUP_ROOT:-$HOME/byeoldori-backups}"
MARK="# byeoldori-backup"
# 새벽 3시 30분(KST)에 돈다. VM 시간대가 KST 가 아니면 UTC 로 보고 18:30 UTC 로 맞춘다.
if [ "$(date +%z)" = "+0900" ]; then HOUR=3; else HOUR=18; fi
mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"
LINE="30 $HOUR * * * DEPLOY_DIR=$DEPLOY_DIR /bin/bash $DEPLOY_DIR/scripts/backup.sh >> $BACKUP_ROOT/backup.log 2>&1 $MARK"
{ crontab -l 2>/dev/null | grep -vF "$MARK" || true; echo "$LINE"; } | crontab -
echo "[backup-cron] 설치됨: $LINE"
