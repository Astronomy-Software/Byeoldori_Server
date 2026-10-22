#!/bin/bash
# 백업 신선도 확인. backup.sh 의 실패 알림은 스크립트 안의 trap 에서만 나가서, cron 항목이 없거나 cron 데몬이 멈춰
# 백업이 아예 돌지 않으면 아무 알림도 없었다. 이 스크립트는 바깥에서 cron 상태와 결과물(완성 백업)을 본다.
# 배포(deploy.yml)와 매일 외부 확인(backup-check.yml)이 호출한다. 문제가 있으면 이유를 한 줄로 출력하고 1 로 끝난다.
#   bash ~/byeoldori-server/scripts/check-backup-freshness.sh
# 설정(선택): ~/.byeoldori-backup.env 의 BACKUP_ROOT, BACKUP_MAX_AGE_HOURS(기본 26 — 매일 03:30 백업 + 여유 2시간)
set -uo pipefail
export PATH="$HOME/bin:$HOME/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

[ -f "$HOME/.byeoldori-backup.env" ] && . "$HOME/.byeoldori-backup.env"
BACKUP_ROOT="${BACKUP_ROOT:-$HOME/byeoldori-backups}"
MAX_AGE_HOURS="${BACKUP_MAX_AGE_HOURS:-26}"
MARK="# byeoldori-backup"   # install-backup-cron.sh 가 붙이는 표식
BACKUP_DIR_PATTERN='20[0-9][0-9][0-1][0-9][0-3][0-9]-[0-2][0-9][0-5][0-9][0-5][0-9]'

PROBLEMS=""
add_problem() { PROBLEMS="${PROBLEMS:+$PROBLEMS, }$1"; }

crontab -l 2>/dev/null | grep -qF "$MARK" || add_problem "백업 cron 항목 없음"
pgrep -x cron >/dev/null 2>&1 || pgrep -x crond >/dev/null 2>&1 || add_problem "cron 데몬이 실행 중이 아님"

# 체크섬까지 끝난(SHA256SUMS 가 있는) 백업 중 가장 최근 것. 폴더 이름이 시각이라 이름순 마지막이 최신이다.
LAST=""
for d in "$BACKUP_ROOT"/20*; do
  [[ "$(basename "$d")" == $BACKUP_DIR_PATTERN ]] && [ -f "$d/SHA256SUMS" ] && LAST="$d"
done
if [ -z "$LAST" ]; then
  add_problem "완성된 백업이 하나도 없음($BACKUP_ROOT)"
elif [ -z "$(find "$LAST/SHA256SUMS" -mmin -$((MAX_AGE_HOURS * 60)) 2>/dev/null)" ]; then
  add_problem "최근 ${MAX_AGE_HOURS}시간 안에 완성된 백업 없음(마지막: $(basename "$LAST"), 로그: $BACKUP_ROOT/backup.log)"
fi

if [ -n "$PROBLEMS" ]; then
  echo "$PROBLEMS"
  exit 1
fi
echo "백업 정상: 마지막 완성 백업 $(basename "$LAST")"
