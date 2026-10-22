#!/bin/bash
# 운영 데이터 백업: MySQL(byeoldori) · MongoDB(교육 프로그램·퀴즈) · 업로드 파일(uploads 볼륨).
# VM 의 cron 이 매일 실행한다(설치: scripts/install-backup-cron.sh, 배포 때 자동). 수동 실행도 된다.
#   bash ~/byeoldori-server/scripts/backup.sh
# 결과: $BACKUP_ROOT/<YYYYMMDD-HHMMSS>/ 아래 mysql-byeoldori.sql.gz, mongo.archive.gz, uploads.tar.gz, SHA256SUMS
# 복원 절차는 README 의 "운영 데이터 백업과 복원" 참고.
#
# 설정(선택): ~/.byeoldori-backup.env 에 아래 값을 두면 읽는다.
#   BACKUP_ROOT=~/byeoldori-backups   로컬 보관 위치
#   BACKUP_KEEP_DAYS=7                로컬 보관 일수(지난 백업 폴더는 로컬 백업이 끝난 실행 뒤 자동 삭제.
#                                     디스크가 모자라면 시작 전에도 지운다. 가장 최근 완성 백업 하나는 항상 남긴다)
#   BACKUP_MAX_DISK_PCT=90            이번 백업을 쓴 뒤의 예상 사용률이 이 값 이상이면 백업하지 않고 실패 알림
#   BACKUP_OCI_BUCKET=                OCI Object Storage 버킷 이름. 지정하면 oci CLI 로 VM 밖에 올린다.
#                                     (로컬 백업은 같은 부트볼륨이라 VM 자체를 잃으면 함께 사라진다)
# 실패하면 monitoring/secrets.env 의 DISCORD_WEBHOOK_URL 로 알린다.
# DB 비밀번호는 각 컨테이너 안의 환경변수를 쓰므로 이 스크립트와 호스트 프로세스 목록에 나오지 않는다.
set -euo pipefail
export PATH="$HOME/bin:$HOME/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

[ -f "$HOME/.byeoldori-backup.env" ] && . "$HOME/.byeoldori-backup.env"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/byeoldori-server}"
BACKUP_ROOT="${BACKUP_ROOT:-$HOME/byeoldori-backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-7}"
MAX_DISK_PCT="${BACKUP_MAX_DISK_PCT:-90}"
OCI_BUCKET="${BACKUP_OCI_BUCKET:-}"
STAMP=$(date +%Y%m%d-%H%M%S)
DEST="$BACKUP_ROOT/$STAMP"

# 백업 파일에는 회원 개인정보가 들어 있으므로 소유자만 읽게 만든다.
umask 077
mkdir -p "$BACKUP_ROOT"

log() { echo "[$(date '+%F %T')] [backup] $*"; }

BACKUP_DIR_PATTERN='20[0-9][0-9][0-1][0-9][0-3][0-9]-[0-2][0-9][0-5][0-9][0-5][0-9]'

# 체크섬까지 끝난(SHA256SUMS 가 있는) 가장 최근 백업 폴더. 없으면 빈 문자열.
latest_backup() {
  local d last=""
  for d in "$BACKUP_ROOT"/20*; do
    [[ "$(basename "$d")" == $BACKUP_DIR_PATTERN ]] && [ -f "$d/SHA256SUMS" ] && last="$d"
  done
  echo "$last"
}

# 보관 기간이 지난 백업 폴더를 지운다. 백업이 며칠째 실패해도 복원할 것이 남도록 가장 최근 완성 백업 하나는 남긴다.
prune_old_backups() {
  local keep
  keep=$(basename "$(latest_backup)")
  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -name "$BACKUP_DIR_PATTERN" ! -name "${keep:-none}" -mtime +"$KEEP_DAYS" -exec rm -rf {} +
}

# $1 KB 를 더 썼을 때의 디스크 사용률(%, 올림). df 의 Use% 와 같은 식(used / (used + avail)).
disk_pct_after() {
  df -Pk "$BACKUP_ROOT" | awk -v add="$1" 'NR==2 { t = $3 + $4; p = ($3 + add) * 100 / t; printf "%d", (p == int(p)) ? p : int(p) + 1 }'
}

notify_fail() {
  local url
  url=$(grep -E '^DISCORD_WEBHOOK_URL=' "$DEPLOY_DIR/monitoring/secrets.env" 2>/dev/null | head -1 | cut -d= -f2- || true)
  [ -z "$url" ] && return 0
  curl -s --max-time 10 -H "Content-Type: application/json" \
    -d "{\"embeds\":[{\"title\":\"❌ 별도리 데이터 백업 실패\",\"description\":\"$1 (VM 로그: $BACKUP_ROOT/backup.log)\",\"color\":15158332}]}" \
    "$url" >/dev/null || true
}

STEP="시작"
LOCAL_DONE=0   # 체크섬까지 끝난 로컬 백업이 있으면 1. 이후 단계(OCI 업로드 등)가 실패해도 지우지 않는다.
on_exit() {
  local rc=$?
  if [ "$rc" -ne 0 ]; then
    log "실패(rc=$rc, 단계: $STEP)"
    if [ "$LOCAL_DONE" = "1" ]; then
      log "로컬 백업은 유지: $DEST"
      notify_fail "단계: $STEP, rc=$rc. 로컬 백업은 남아 있음($DEST)"
    else
      rm -rf "$DEST"   # 반쪽짜리 백업은 남기지 않는다
      notify_fail "단계: $STEP, rc=$rc"
    fi
  fi
}
trap on_exit EXIT

# 동시 실행 방지(수동 실행과 cron 이 겹칠 때)
if command -v flock >/dev/null; then
  exec 9>"$BACKUP_ROOT/.lock"
  flock -n 9 || { log "다른 백업이 실행 중이라 건너뜀"; exit 0; }
fi

STEP="디스크 확인"
# 백업 폴더는 MySQL·Loki 와 같은 부트볼륨이라, 이번 백업을 다 쓴 뒤의 사용률로 판단한다.
# 이번 크기는 직전 완성 백업 크기로 어림한다(uploads 는 이미지라 gzip 으로 거의 줄지 않아 매번 비슷하다).
LATEST=$(latest_backup)
NEED_KB=0
[ -n "$LATEST" ] && NEED_KB=$(du -sk "$LATEST" | cut -f1)
USED_PCT=$(disk_pct_after 0)
AFTER_PCT=$(disk_pct_after "$NEED_KB")
# 디스크를 가장 꾸준히 채우는 것은 이 백업 자체다. 여기서 정리 없이 바로 끝내면 오래된 백업이 영영 지워지지 않아
# 매일 같은 자리에서 멈추므로, 보관 기간이 지난 백업부터 지우고 다시 잰다.
if [ "$AFTER_PCT" -ge "$MAX_DISK_PCT" ]; then
  log "디스크 사용률 ${USED_PCT}%(이번 백업 예상 ${NEED_KB}KB 포함 ${AFTER_PCT}%) ≥ ${MAX_DISK_PCT}% — 보관 기간 지난 백업부터 정리"
  prune_old_backups
  USED_PCT=$(disk_pct_after 0)
  AFTER_PCT=$(disk_pct_after "$NEED_KB")
fi
if [ "$AFTER_PCT" -ge "$MAX_DISK_PCT" ]; then
  log "디스크 사용률 ${USED_PCT}%(이번 백업 예상 포함 ${AFTER_PCT}%) ≥ ${MAX_DISK_PCT}% — 백업 중단"
  STEP="디스크 사용률 ${USED_PCT}%(백업 후 예상 ${AFTER_PCT}%)"
  exit 1
fi
mkdir -p "$DEST"
log "시작 → $DEST (디스크 ${USED_PCT}%)"

STEP="MySQL 덤프"
# --add-drop-database: 복원할 때 byeoldori 를 지우고 다시 만든다. 없으면 백업 뒤에 생긴 테이블(새 마이그레이션)이 남아
# flyway_schema_history 만 되돌아가고, 다음 기동 때 같은 마이그레이션이 'Table already exists' 로 실패한다.
docker exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF --add-drop-database --databases byeoldori' \
  | gzip > "$DEST/mysql-byeoldori.sql.gz.part"
gzip -t "$DEST/mysql-byeoldori.sql.gz.part"
# 덤프가 끝까지 쓰였는지(mysqldump 는 정상 종료 시 마지막 줄에 Dump completed 를 남긴다)
gzip -dc "$DEST/mysql-byeoldori.sql.gz.part" | tail -n 1 | grep -q "Dump completed"
mv "$DEST/mysql-byeoldori.sql.gz.part" "$DEST/mysql-byeoldori.sql.gz"

STEP="MongoDB 덤프"
docker exec mongodb sh -c 'exec mongodump --quiet --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive --gzip' \
  > "$DEST/mongo.archive.gz.part"
[ -s "$DEST/mongo.archive.gz.part" ]
mv "$DEST/mongo.archive.gz.part" "$DEST/mongo.archive.gz"

STEP="업로드 파일 묶기"
# compose 가 붙이는 볼륨 라벨로 uploads 볼륨을 찾는다(앱 컨테이너가 꺼져 있어도 된다).
PROJECT=$(basename "$DEPLOY_DIR")
UPLOADS_VOL=$(docker volume ls -q --filter "label=com.docker.compose.volume=uploads" --filter "label=com.docker.compose.project=$PROJECT" | head -1)
[ -n "$UPLOADS_VOL" ] || UPLOADS_VOL=$(docker volume ls -q --filter "label=com.docker.compose.volume=uploads" | head -1)
[ -n "$UPLOADS_VOL" ]
# 앱 이미지(byeoldori-server:local)에 tar 가 있으므로 별도 이미지를 받지 않는다. 볼륨은 읽기 전용으로 붙이고,
# 소유자와 무관하게 전부 읽도록 root 로 돈다(네트워크 없음).
docker run --rm --network none --user 0 --entrypoint tar -v "$UPLOADS_VOL":/data/uploads:ro byeoldori-server:local \
  -czf - -C /data/uploads . > "$DEST/uploads.tar.gz.part"
gzip -t "$DEST/uploads.tar.gz.part"
mv "$DEST/uploads.tar.gz.part" "$DEST/uploads.tar.gz"

STEP="체크섬"
(cd "$DEST" && sha256sum mysql-byeoldori.sql.gz mongo.archive.gz uploads.tar.gz > SHA256SUMS)
LOCAL_DONE=1
log "로컬 백업 완료: $(du -sh "$DEST" | cut -f1)"

# 업로드가 실패해도(키 만료·네트워크·CLI 없음) 로컬 백업과 오래된 백업 정리는 그대로 진행하고, 마지막에 실패로 끝내 알린다.
# (여기서 바로 종료하면 정리가 매일 건너뛰어져 디스크가 차고, 결국 디스크 확인 단계에서 백업이 멈춘다)
upload_oci() {
  command -v oci >/dev/null || { log "oci CLI 를 찾을 수 없음"; return 1; }
  local f
  for f in mysql-byeoldori.sql.gz mongo.archive.gz uploads.tar.gz SHA256SUMS; do
    oci os object put --bucket-name "$OCI_BUCKET" --file "$DEST/$f" --name "byeoldori/$STAMP/$f" --force >/dev/null || return 1
  done
}
OCI_FAILED=0
if [ -n "$OCI_BUCKET" ]; then
  STEP="OCI Object Storage 업로드"
  if upload_oci; then
    log "OCI 업로드 완료: $OCI_BUCKET/byeoldori/$STAMP/"
  else
    OCI_FAILED=1
    log "OCI 업로드 실패 — 로컬 백업은 유지"
  fi
fi

STEP="오래된 로컬 백업 정리"
prune_old_backups

if [ "$OCI_FAILED" = "1" ]; then
  STEP="OCI Object Storage 업로드"
  exit 1
fi
log "끝"
