#!/bin/bash
# scripts/check-backup-freshness.sh 테스트. crontab·pgrep 을 가짜 명령으로 바꿔 VM 없이 돈다.
#   bash scripts/tests/backup_freshness_test.sh
# 검사 스크립트는 PATH 를 $HOME/bin 부터 다시 잡으므로 HOME 을 임시 폴더로 두고 가짜 명령을 그 bin 에 넣는다.
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/check-backup-freshness.sh"
FAILS=0

# $1: crontab 에 백업 항목이 있으면 1, $2: cron 데몬이 돌면 1
setup() {
  T=$(mktemp -d)
  mkdir -p "$T/bin" "$T/backups"
  if [ "$1" = "1" ]; then
    printf '#!/bin/bash\necho "30 18 * * * /bin/bash backup.sh # byeoldori-backup"\n' > "$T/bin/crontab"
  else
    printf '#!/bin/bash\necho "no crontab for ubuntu" >&2\nexit 1\n' > "$T/bin/crontab"
  fi
  if [ "$2" = "1" ]; then
    printf '#!/bin/bash\n[ "$2" = "cron" ]\n' > "$T/bin/pgrep"
  else
    printf '#!/bin/bash\nexit 1\n' > "$T/bin/pgrep"
  fi
  chmod +x "$T/bin/"*
  printf 'BACKUP_ROOT=%s\n' "$T/backups" > "$T/.byeoldori-backup.env"
}

# $1: 폴더 이름, $2: SHA256SUMS 시각(touch -d), $3: 1 이면 SHA256SUMS 없이(미완성) 만든다
add_backup() {
  mkdir -p "$T/backups/$1"
  [ "${3:-}" = "1" ] && return 0
  : > "$T/backups/$1/SHA256SUMS"
  touch -d "$2" "$T/backups/$1/SHA256SUMS"
}

run_check() {
  set +e
  HOME="$T" bash "$SCRIPT" > "$T/out.log" 2>&1
  RC=$?
  set -e
}

check() {
  if eval "$2"; then echo "  ok: $1"; else echo "  FAIL: $1"; FAILS=$((FAILS + 1)); sed 's/^/    | /' "$T/out.log"; fi
}

echo "[1] cron 항목·데몬이 있고 26시간 안에 완성 백업이 있으면 정상"
setup 1 1
add_backup 20261020-033000 '3 days ago'
add_backup 20261022-033000 '5 hours ago'
run_check
check "성공 코드" '[ "$RC" -eq 0 ]'
check "마지막 백업 표시" 'grep -q "20261022-033000" "$T/out.log"'
rm -rf "$T"

echo "[2] 마지막 완성 백업이 26시간보다 오래됐으면 실패(미완성 폴더는 무시)"
setup 1 1
add_backup 20261020-033000 '30 hours ago'
add_backup 20261022-033000 '' 1
run_check
check "실패 코드" '[ "$RC" -ne 0 ]'
check "오래된 백업 사유와 마지막 완성 백업" 'grep -q "최근 26시간 안에 완성된 백업 없음(마지막: 20261020-033000" "$T/out.log"'
rm -rf "$T"

echo "[3] 완성 백업이 하나도 없으면 실패"
setup 1 1
run_check
check "실패 코드" '[ "$RC" -ne 0 ]'
check "백업 없음 사유" 'grep -q "완성된 백업이 하나도 없음" "$T/out.log"'
rm -rf "$T"

echo "[4] 백업은 최근 것이 있어도 cron 항목이 없거나 데몬이 멈췄으면 실패(다음 백업이 돌지 않음)"
setup 0 0
add_backup 20261022-033000 '1 hour ago'
run_check
check "실패 코드" '[ "$RC" -ne 0 ]'
check "cron 항목 없음 사유" 'grep -q "백업 cron 항목 없음" "$T/out.log"'
check "cron 데몬 사유" 'grep -q "cron 데몬이 실행 중이 아님" "$T/out.log"'
rm -rf "$T"

echo "[5] BACKUP_MAX_AGE_HOURS 로 기준을 바꿀 수 있다"
setup 1 1
add_backup 20261022-033000 '30 hours ago'
echo "BACKUP_MAX_AGE_HOURS=48" >> "$T/.byeoldori-backup.env"
run_check
check "48시간 기준이면 정상" '[ "$RC" -eq 0 ]'
rm -rf "$T"

[ "$FAILS" -eq 0 ] && echo "backup_freshness_test: 모두 통과" || { echo "backup_freshness_test: 실패 $FAILS 건"; exit 1; }
