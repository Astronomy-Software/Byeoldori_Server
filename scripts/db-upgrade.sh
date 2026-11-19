#!/bin/bash
# 운영 DB 메이저 업그레이드(EOL 해소): MySQL 8.0 → 8.4 LTS(제자리), MongoDB 6.0 → 8.0(덤프·복원).
# VM 의 배포 디렉터리에서 사람이 단계별로 실행한다(배포 워크플로가 자동으로 부르지 않음). 순서는 README "DB 메이저 업그레이드" 참고.
#
#   bash scripts/db-upgrade.sh prepare-mongo   # [머지 전] 실행 중인 6.0 을 덤프해 새 볼륨(mongo_data_v8)에 8.0 으로 복원·검증
#   bash scripts/db-upgrade.sh upgrade-mysql   # [머지 후] 같은 볼륨 그대로 mysql 컨테이너를 8.4 로 다시 만든다(앱 1분 내외 DB 끊김)
#   bash scripts/db-upgrade.sh verify          # 버전·행 수 확인
#
# 왜 이렇게 나누나:
# - 배포는 매번 `compose up -d mongo` 를 하므로 compose 의 mongo 이미지만 8.0 으로 바꿔 머지하면 6.0 데이터 디렉터리에 8.0 이
#   떠서 기동에 실패한다(6.0→8.0 제자리 불가, 7.0 을 거쳐야 함). 데이터가 작아 새 볼륨에 미리 복원해 두고 compose 가 새 볼륨을 보게 한다.
#   옛 볼륨(mongo_data)은 그대로 남아 되돌릴 때 쓴다.
# - mysql 은 배포가 다시 만들지 않으므로(app 만 --no-deps) 머지 뒤 수동으로 한 번 다시 만든다. 8.4 는 옛 옵션
#   --default-authentication-plugin 을 모르면 기동하지 않아 --mysql-native-password=ON 으로 바꿨다(compose).
#   8.4 → 8.0 제자리 되돌리기는 안 되므로 직전 백업이 필수다(아래에서 확인).
# 비밀번호는 컨테이너 안의 환경변수를 쓰고, 명령줄·출력에 나오지 않게 한다(backup.sh 와 같은 방식).
set -euo pipefail
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/byeoldori-server}"
BACKUP_ROOT="${BACKUP_ROOT:-$HOME/byeoldori-backups}"
cd "$DEPLOY_DIR"
COMPOSE=(docker compose -f docker-compose.oci.yml -f docker-compose.mongo.yml)
PREP=mongo-v8-prep

log() { echo "[db-upgrade] $*"; }
die() { echo "[db-upgrade] 중단: $*" >&2; exit 1; }

project() { docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$1"; }

mysql_q() { docker exec mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B -e "$1" byeoldori' sh "$1"; }

mysql_counts() {
  local t out=""
  for t in users community comment observation_site refresh_tokens flyway_schema_history; do
    out="$out $t=$(mysql_q "select count(*) from $t" 2>/dev/null || echo '?')"
  done
  echo "$out"
}

# 컨테이너 안에서 root 계정으로 mongodump/mongorestore 를 돌린다. 비밀번호는 소유자 전용 임시 설정 파일(--config)로만 넘긴다.
# YAML 작은따옴표 문자열이므로 비밀번호 안의 ' 는 '' 로 바꾼다(backup.sh 와 같음).
MONGO_TOOL_SCRIPT=$(cat <<'EOF'
set -e
umask 077
cfg=$(mktemp)
trap 'rm -f "$cfg"' EXIT
printf "password: '%s'\n" "$(printf '%s' "$MONGO_INITDB_ROOT_PASSWORD" | sed "s/'/''/g")" > "$cfg"
tool="$1"; shift
"$tool" --quiet --config="$cfg" --username "$MONGO_INITDB_ROOT_USERNAME" --authenticationDatabase admin "$@"
EOF
)
mongo_tool() { # $1=컨테이너, $2=도구(mongodump|mongorestore), 나머지=인자
  local c="$1"; shift
  docker exec -i "$c" sh -c "$MONGO_TOOL_SCRIPT" sh "$@"
}

# byeoldori DB 의 컬렉션별 문서 수. 인증은 JS 안에서 컨테이너 환경변수로 해 명령줄에 비밀번호가 나오지 않게 한다.
MONGO_COUNT_JS='db.getSiblingDB("admin").auth(process.env.MONGO_INITDB_ROOT_USERNAME, process.env.MONGO_INITDB_ROOT_PASSWORD); const x = db.getSiblingDB("byeoldori"); print(x.getCollectionNames().sort().map(c => c + "=" + x[c].countDocuments()).join(" "))'
mongo_counts() { # $1=컨테이너
  docker exec "$1" mongosh --quiet --eval "$MONGO_COUNT_JS" 2>/dev/null
}

require_recent_backup() {
  local latest
  latest=$(ls -d "$BACKUP_ROOT"/2026* 2>/dev/null | tail -1 || true)
  [ -n "$latest" ] || die "백업이 없다. 먼저 bash scripts/backup.sh"
  if [ -n "$(find "$latest" -maxdepth 0 -mmin +60)" ]; then
    die "최근 백업($latest)이 1시간보다 오래됐다. 먼저 bash scripts/backup.sh 로 새로 백업"
  fi
  (cd "$latest" && sha256sum -c SHA256SUMS >/dev/null) || die "최근 백업 체크섬 불일치: $latest"
  log "직전 백업 확인: $latest"
}

prepare_mongo() {
  local cur proj vol
  cur=$(docker exec mongodb mongod --version | head -1)
  case "$cur" in *v6.*) ;; *) die "실행 중인 mongodb 가 6.x 가 아니다: $cur";; esac
  proj=$(project mongodb)
  vol="${proj}_mongo_data_v8"
  if docker volume inspect "$vol" >/dev/null 2>&1; then
    die "$vol 볼륨이 이미 있다. 다시 하려면 내용을 확인한 뒤 docker volume rm $vol"
  fi
  # compose 가 자기 볼륨으로 알아보도록 같은 라벨로 만든다(없으면 '외부 볼륨' 경고)
  docker volume create --label com.docker.compose.project="$proj" --label com.docker.compose.volume=mongo_data_v8 "$vol" >/dev/null
  log "새 볼륨 $vol 생성 → mongo:8.0 임시 컨테이너로 초기화"
  # root 계정 값은 현재 컨테이너에서 읽어 소유자 전용 env 파일로만 넘긴다(docker run 명령줄에 안 나오게)
  local envf; envf=$(mktemp); chmod 600 "$envf"
  docker exec mongodb sh -c 'printf "MONGO_INITDB_ROOT_USERNAME=%s\nMONGO_INITDB_ROOT_PASSWORD=%s\nMONGO_INITDB_DATABASE=byeoldori\n" "$MONGO_INITDB_ROOT_USERNAME" "$MONGO_INITDB_ROOT_PASSWORD"' > "$envf"
  docker rm -f "$PREP" >/dev/null 2>&1 || true
  docker run -d --name "$PREP" --env-file "$envf" -v "$vol":/data/db mongo:8.0 >/dev/null
  rm -f "$envf"
  for _ in $(seq 1 60); do mongo_counts "$PREP" >/dev/null && break; sleep 2; done

  log "6.0 덤프 → 8.0 복원(byeoldori DB 만)"
  mongo_tool mongodb mongodump --db byeoldori --archive --gzip | mongo_tool "$PREP" mongorestore --archive --gzip --drop --nsInclude 'byeoldori.*'

  local before after
  before=$(mongo_counts mongodb); after=$(mongo_counts "$PREP")
  log "6.0: $before"
  log "8.0: $after"
  docker stop "$PREP" >/dev/null && docker rm "$PREP" >/dev/null
  [ "$before" = "$after" ] || die "컬렉션 문서 수가 다르다(볼륨 $vol 은 남겨 둠, 확인 후 지울 것)"
  log "준비 완료. 이제 PR 을 머지하면 다음 배포의 'compose up -d mongo' 가 8.0 + $vol 로 바꾼다. 그 사이 생긴 퀴즈 응시 기록은 옮겨지지 않으니 조용한 시간에."
}

upgrade_mysql() {
  grep -q 'image: mysql:8.4' docker-compose.oci.yml || die "docker-compose.oci.yml 이 아직 mysql:8.4 가 아니다(업그레이드 PR 머지·배포 뒤에 실행)"
  local cur; cur=$(mysql_q "select version()")
  case "$cur" in 8.4.*) log "이미 $cur"; return 0;; 8.0.*) ;; *) die "예상하지 못한 mysql 버전: $cur";; esac
  require_recent_backup
  local before; before=$(mysql_counts)
  log "8.0 행 수:$before"
  log "mysql 컨테이너를 8.4 로 다시 만든다(같은 볼륨, 앱은 잠시 DB 연결이 끊겼다가 다시 붙는다)"
  "${COMPOSE[@]}" up -d --no-deps mysql
  for _ in $(seq 1 90); do mysql_q "select 1" >/dev/null 2>&1 && break; sleep 2; done
  local now; now=$(mysql_q "select version()")
  case "$now" in 8.4.*) ;; *) die "8.4 로 뜨지 않았다($now). docker logs mysql 확인, 되돌리기는 README 참고";; esac
  bash scripts/ensure-mysql-ft-stopword-off.sh
  local after; after=$(mysql_counts)
  log "8.4($now) 행 수:$after"
  [ "$before" = "$after" ] || die "행 수가 다르다 — 앱 쓰기와 겹쳤을 수 있으니 직접 비교"
  log "완료. 앱 health 확인: curl -s https://api.byeoldori.com/actuator/health/warm"
}

# 배포가 'compose up -d mongo' 를 해도 되는지. 새 VM(mongodb 컨테이너 없음)이거나 새 볼륨이 준비돼 있으면 0.
# 준비 전이면 1 → 배포는 mongo 를 건드리지 않아 6.0 이 옛 볼륨으로 계속 돈다(빈 새 볼륨으로 바뀌는 사고 방지).
mongo_ready() {
  docker inspect mongodb >/dev/null 2>&1 || return 0
  docker volume inspect "$(project mongodb)_mongo_data_v8" >/dev/null 2>&1
}

verify() {
  log "mysql $(mysql_q 'select version()'):$(mysql_counts)"
  log "mongo $(docker exec mongodb mongod --version | head -1): $(mongo_counts mongodb)"
}

case "${1:-}" in
  prepare-mongo) prepare_mongo ;;
  upgrade-mysql) upgrade_mysql ;;
  verify) verify ;;
  mongo-ready) mongo_ready ;;
  *) echo "사용: bash scripts/db-upgrade.sh {prepare-mongo|upgrade-mysql|verify|mongo-ready}" >&2; exit 2 ;;
esac
