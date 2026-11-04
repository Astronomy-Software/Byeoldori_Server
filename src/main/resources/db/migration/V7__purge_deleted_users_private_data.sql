-- V7: 이미 탈퇴한(소프트 딜리트) 계정에 남아 있던 개인 데이터를 한 번 정리한다 (데이터만 지우고 스키마는 그대로).
-- 예전 탈퇴 처리(UserService.deleteAccount)는 토큰만 지우고 users 행을 익명화해, 본인만 보던 관측 일정
-- (제목·메모·좌표·장소명)·일정 사진 행·즐겨찾기 좌표·알림이 탈퇴 뒤에도 무기한 남았다.
-- 이제 탈퇴 시 AccountDataPurgeService 가 지우므로, 이전에 탈퇴한 계정에도 같은 정리를 적용한다.
-- 공개 게시글·댓글·좋아요·평점은 "탈퇴한 사용자"로 남기는 설계라 건드리지 않는다.
-- uploaded_file(업로더 기록)도 지우지 않는다 → 사진 실제 파일을 나중에 소유자 기준으로 정리할 근거로 남긴다.
-- MongoDB 퀴즈 응시 기록은 Flyway 범위 밖이라 여기서 다루지 않는다.
-- 대상이 없으면 아무 행도 지우지 않으므로 다시 실행해도 결과가 같다.
-- (H2 회귀 테스트 AccountDataPurgeMigrationTest 가 이 파일을 그대로 실행하므로 표준 SQL 로만 쓴다)

-- 1) 일정의 대상 천체 태그 (content_target 은 FK 가 없어 일정과 함께 지워지지 않는다)
DELETE FROM content_target
WHERE content_type = 'EVENT'
  AND content_id IN (
    SELECT e.id FROM observation_event e JOIN users u ON u.id = e.user_id WHERE u.deleted_at IS NOT NULL
  );

-- 2) 일정 사진 행 (FK ON DELETE CASCADE 가 있지만 의존하지 않고 명시적으로 지운다)
DELETE FROM calendar_image
WHERE event_id IN (
    SELECT e.id FROM observation_event e JOIN users u ON u.id = e.user_id WHERE u.deleted_at IS NOT NULL
  );

-- 3) 관측 일정
DELETE FROM observation_event
WHERE user_id IN (SELECT u.id FROM users u WHERE u.deleted_at IS NOT NULL);

-- 4) 즐겨찾기 관측지(직접 지정한 좌표·이름 포함)
DELETE FROM user_saved_sites
WHERE user_id IN (SELECT u.id FROM users u WHERE u.deleted_at IS NOT NULL);

-- 5) 받은 알림
DELETE FROM notifications
WHERE user_id IN (SELECT u.id FROM users u WHERE u.deleted_at IS NOT NULL);
