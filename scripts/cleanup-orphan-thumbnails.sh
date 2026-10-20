#!/usr/bin/env bash
# 원본이 없는 고아 썸네일(thumbnails/yyyy/MM/dd/<uuid>.jpg)을 정리한다. 1회성 운영 작업.
#
# 예전 storeImage 는 썸네일을 원본과 무관한 무작위 이름으로 만들어 아무도 쓰지 않는 파일이 쌓였다.
# 이제 썸네일 이름은 원본 uuid 와 같으므로, 같은 날짜 폴더에 같은 uuid 의 원본이 없는 썸네일은 고아다.
# 원본 이미지·첨부·json 은 건드리지 않는다. 기본은 목록만 출력(dry-run), --apply 를 줘야 지운다.
#
# 사용(VM, 앱 컨테이너 안에서 실행 — 업로드 볼륨은 컨테이너의 /data/uploads):
#   docker exec -i <app 컨테이너> bash -s < scripts/cleanup-orphan-thumbnails.sh              # 확인만
#   docker exec -i <app 컨테이너> bash -s -- --apply < scripts/cleanup-orphan-thumbnails.sh   # 실제 삭제
set -euo pipefail

BASE_DIR="${STORAGE_LOCAL_BASE_DIR:-/data/uploads}"
APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

THUMB_ROOT="$BASE_DIR/thumbnails"
[ -d "$THUMB_ROOT" ] || { echo "썸네일 폴더 없음: $THUMB_ROOT"; exit 0; }

count=0; bytes=0
while IFS= read -r -d '' thumb; do
  rel="${thumb#"$THUMB_ROOT"/}"          # yyyy/MM/dd/<uuid>.jpg
  dir="$(dirname "$rel")"; name="$(basename "$rel" .jpg)"
  if ! compgen -G "$BASE_DIR/$dir/$name.*" > /dev/null; then
    size=$(stat -c %s "$thumb"); count=$((count + 1)); bytes=$((bytes + size))
    if [ "$APPLY" = 1 ]; then rm -f -- "$thumb"; else echo "$thumb"; fi
  fi
done < <(find "$THUMB_ROOT" -type f -name '*.jpg' -print0)

mode=$([ "$APPLY" = 1 ] && echo "삭제함" || echo "삭제 대상(dry-run)")
echo "고아 썸네일 $count 개, $((bytes / 1024 / 1024))MB — $mode"
