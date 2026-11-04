#!/bin/bash
# 운영 MySQL 의 FULLTEXT 불용어 사용(innodb_ft_enable_stopword)을 GLOBAL 로 끄고 재시작 후에도 유지되게 한다(멱등).
# ngram 파서는 불용어를 "포함한" 토큰을 색인에서 빼므로, 기본 불용어('a'·'i'·'on'·'or' 등)가 켜져 있으면
# 'Orion' 같은 영문 제목 검색이 항상 0건이 된다. V8 마이그레이션의 FULLTEXT 재색인은 이 값이 OFF 일 때만 효과가 있으므로
# 배포(.github/workflows/deploy.yml)가 새 앱 기동(=Flyway 실행) 전에 매번 호출한다. 수동: bash scripts/ensure-mysql-ft-stopword-off.sh
#
# SET PERSIST 는 GLOBAL 값을 바로 바꾸고 데이터 볼륨(mysqld-auto.cnf)에 저장하므로 컨테이너 재생성·DB 중단이 필요 없다.
# docker-compose.oci.yml 의 --innodb-ft-enable-stopword=0 은 mysql 컨테이너를 새로 만들 때를 위한 같은 값이다.
# DB 비밀번호는 컨테이너 안의 환경변수를 쓰므로 호스트 프로세스 목록에 나오지 않는다(backup.sh 와 같은 방식).
# 테스트(scripts/tests/ft_stopword_test.sh)는 PATH 의 가짜 docker 로 돈다.
set -euo pipefail
CONTAINER="${MYSQL_CONTAINER:-mysql}"

mysql_exec() {
  docker exec "$CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B -e "$1"' sh "$1"
}

current() { mysql_exec "SELECT @@GLOBAL.innodb_ft_enable_stopword" | tr -d '[:space:]'; }

CUR=$(current)
if [ "$CUR" = "0" ]; then
  echo "[ft-stopword] 이미 OFF"
  exit 0
fi
echo "[ft-stopword] 현재 값 '$CUR' → OFF 로 설정(SET PERSIST)"
mysql_exec "SET PERSIST innodb_ft_enable_stopword = OFF" >/dev/null
CUR=$(current)
if [ "$CUR" != "0" ]; then
  echo "[ft-stopword] 설정 실패(현재 '$CUR') — V8 재색인이 효과 없으므로 배포를 중단한다" >&2
  exit 1
fi
echo "[ft-stopword] OFF 확인"
