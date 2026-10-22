#!/bin/bash
# scripts/backup.sh 실패 처리 테스트. docker·df·oci 를 가짜 명령으로 바꿔 VM 없이 돈다.
#   bash scripts/tests/backup_test.sh
# backup.sh 는 PATH 를 $HOME/bin 부터 다시 잡으므로 HOME 을 임시 폴더로 두고 가짜 명령을 그 bin 에 넣는다.
set -euo pipefail
SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/backup.sh"
FAILS=0

setup() {
  T=$(mktemp -d)
  mkdir -p "$T/bin" "$T/backups" "$T/deploy"
  # 전체 10000KB 디스크. FAKE_DF_USED(KB) 로 사용량을 정하고, FAKE_DF_FREED_BY 경로가 지워지면
  # FAKE_DF_USED_AFTER 로 줄어든다(정리로 공간이 생긴 상황).
  cat > "$T/bin/df" <<'EOF'
#!/bin/bash
used=${FAKE_DF_USED:-1000}
if [ -n "${FAKE_DF_FREED_BY:-}" ] && [ ! -e "$FAKE_DF_FREED_BY" ]; then used=${FAKE_DF_USED_AFTER:-1000}; fi
echo "Filesystem 1024-blocks Used Available Capacity Mounted on"
echo "/dev/sda1 10000 $used $((10000 - used)) $((used / 100))% /"
EOF
  cat > "$T/bin/docker" <<'EOF'
#!/bin/bash
case "$1 $2" in
  "exec mysql")
    echo "$*" >> "$HOME/docker.log"
    [ -n "${FAKE_MYSQL_FAIL:-}" ] && exit 1
    printf 'CREATE TABLE t (id int);\n-- Dump completed on 2026-10-20\n' ;;
  "exec mongodb") printf 'mongo-archive' ;;
  "volume ls") echo "byeoldori-server_uploads" ;;
  run*) echo "uploads" | gzip ;;
  *) exit 1 ;;
esac
EOF
  cat > "$T/bin/oci" <<'EOF'
#!/bin/bash
[ -n "${FAKE_OCI_FAIL:-}" ] && exit 1
echo "$*" >> "$HOME/oci.log"
EOF
  chmod +x "$T/bin/"*
  printf 'BACKUP_ROOT=%s\nBACKUP_OCI_BUCKET=%s\n' "$T/backups" "${1:-}" > "$T/.byeoldori-backup.env"
  # 보관 기간(7일)이 지난 예전 백업 하나
  mkdir -p "$T/backups/20200101-000000"
  touch -d '30 days ago' "$T/backups/20200101-000000"
}

run_backup() {
  set +e
  HOME="$T" DEPLOY_DIR="$T/deploy" bash "$SCRIPT" > "$T/out.log" 2>&1
  RC=$?
  set -e
}

# 보관 기간이 지난 '완성' 백업(SHA256SUMS 있음) 하나를 더 둔다. $1: 채울 크기(KB, 예상 크기 확인용)
add_old_complete_backup() {
  mkdir -p "$T/backups/20200102-000000"
  : > "$T/backups/20200102-000000/SHA256SUMS"
  [ -n "${1:-}" ] && head -c "$(( $1 * 1024 ))" /dev/zero > "$T/backups/20200102-000000/uploads.tar.gz"
  touch -d '29 days ago' "$T/backups/20200102-000000"
}

backup_dirs() { find "$T/backups" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' '; }

new_backups() { find "$T/backups" -mindepth 1 -maxdepth 1 -type d ! -name 20200101-000000 | wc -l | tr -d ' '; }

check() {
  if eval "$2"; then echo "  ok: $1"; else echo "  FAIL: $1"; FAILS=$((FAILS + 1)); sed 's/^/    | /' "$T/out.log"; fi
}

echo "[1] OCI 업로드가 실패해도 체크섬까지 끝난 로컬 백업은 남고, 오래된 백업 정리도 돈다"
setup my-bucket
FAKE_OCI_FAIL=1 run_backup
check "실패 코드로 끝남(알림 대상)" '[ "$RC" -ne 0 ]'
check "새 로컬 백업 1개 유지" '[ "$(new_backups)" = "1" ]'
check "유지된 백업에 SHA256SUMS 있음" 'ls "$T"/backups/2*/SHA256SUMS | grep -v 20200101 >/dev/null'
check "보관 기간 지난 백업 삭제" '[ ! -d "$T/backups/20200101-000000" ]'
rm -rf "$T"

echo "[2] 로컬 덤프 단계에서 실패하면 반쪽짜리 백업 폴더는 지운다"
setup
FAKE_MYSQL_FAIL=1 run_backup
check "실패 코드로 끝남" '[ "$RC" -ne 0 ]'
check "새 백업 폴더 없음" '[ "$(new_backups)" = "0" ]'
check "실패 시 정리 단계는 건너뜀(예전 백업 유지)" '[ -d "$T/backups/20200101-000000" ]'
rm -rf "$T"

echo "[3] OCI 업로드까지 성공"
setup my-bucket
run_backup
check "성공 코드" '[ "$RC" -eq 0 ]'
check "새 로컬 백업 1개" '[ "$(new_backups)" = "1" ]'
check "파일 4개 업로드" '[ "$(wc -l < "$T/oci.log" | tr -d " ")" = "4" ]'
check "보관 기간 지난 백업 삭제" '[ ! -d "$T/backups/20200101-000000" ]'
check "덤프에 DROP DATABASE 포함(복원 시 백업 뒤 생긴 테이블까지 지움)" 'grep -q -- "--add-drop-database --databases byeoldori" "$T/docker.log"'
rm -rf "$T"

echo "[4] 디스크가 상한 이상이면 보관 기간 지난 백업부터 지우고, 공간이 나면 백업을 이어간다"
setup
add_old_complete_backup
FAKE_DF_USED=9500 FAKE_DF_FREED_BY="$T/backups/20200101-000000" FAKE_DF_USED_AFTER=5000 run_backup
check "성공 코드" '[ "$RC" -eq 0 ]'
check "정리 후 진행 로그" 'grep -q "보관 기간 지난 백업부터 정리" "$T/out.log"'
check "새 로컬 백업 1개만 남음(예전 백업 모두 정리)" '[ "$(backup_dirs)" = "1" ] && [ ! -d "$T/backups/20200101-000000" ]'
rm -rf "$T"

echo "[5] 정리해도 모자라면 실패로 알리되, 가장 최근 완성 백업은 보관 기간이 지났어도 남긴다"
setup
add_old_complete_backup
FAKE_DF_USED=9500 run_backup
check "실패 코드로 끝남" '[ "$RC" -ne 0 ]'
check "백업 중단 로그" 'grep -q "백업 중단" "$T/out.log"'
check "미완성 예전 백업은 삭제" '[ ! -d "$T/backups/20200101-000000" ]'
check "최근 완성 백업 유지, 새 폴더 없음" '[ -d "$T/backups/20200102-000000" ] && [ "$(backup_dirs)" = "1" ]'
rm -rf "$T"

echo "[6] 지금은 상한 아래여도 이번 백업(직전 백업 크기)을 더하면 상한을 넘을 때는 시작하지 않는다"
setup
add_old_complete_backup 1500
FAKE_DF_USED=8000 run_backup
check "실패 코드로 끝남" '[ "$RC" -ne 0 ]'
check "예상 사용률로 판단" 'grep -q "백업 후 예상 9[0-9]%" "$T/out.log"'
check "새 폴더 없음" '[ -d "$T/backups/20200102-000000" ] && [ "$(backup_dirs)" = "1" ]'
rm -rf "$T"

echo "[7] 직전 백업 크기를 더해도 상한 아래면 그대로 백업한다"
setup
add_old_complete_backup 500
FAKE_DF_USED=8000 run_backup
check "성공 코드" '[ "$RC" -eq 0 ]'
check "새 로컬 백업 1개" '[ "$(backup_dirs)" = "1" ]'
rm -rf "$T"

[ "$FAILS" -eq 0 ] && echo "backup_test: 모두 통과" || { echo "backup_test: 실패 $FAILS 건"; exit 1; }
