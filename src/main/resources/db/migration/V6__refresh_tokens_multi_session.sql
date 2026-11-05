-- V6: 리프레시 토큰을 사용자당 1행 → 로그인(기기·브라우저)마다 1행으로 바꾼다.
-- user_id UNIQUE 때문에 다른 기기에서 로그인하면 기존 행의 해시가 덮어써져, 먼저 로그인한 기기가
-- access 만료(최대 1시간) 시점에 강제 로그아웃됐다. 이제 재발급은 token_hash 로 그 행만 회전한다.
-- 기존 행은 그대로 둔다 → 배포 전에 받은 refresh 토큰도 계속 재발급된다(로그아웃되는 사용자 없음).
-- 엔티티: user/entity/RefreshToken.kt (인덱스·제약 이름을 그대로 맞출 것)

-- 1) FK 가 쓸 일반 인덱스를 먼저 만든다(MySQL 은 FK 가 쓰는 유일한 인덱스를 지울 수 없다)
ALTER TABLE `refresh_tokens` ADD INDEX `idx_refresh_tokens_user_id` (`user_id`);

-- 2) user_id 단일 컬럼 UNIQUE 를 지운다. V1 에서는 UK7tdcd6ab5wsgoudnvj7xf1b7l 이지만
--    운영(ddl-auto 시절 생성)과 이름이 다를 가능성에 대비해 information_schema 에서 이름을 찾는다.
SET @uk_user_id := (
  SELECT s.INDEX_NAME
  FROM information_schema.STATISTICS s
  WHERE s.TABLE_SCHEMA = DATABASE()
    AND s.TABLE_NAME = 'refresh_tokens'
    AND s.NON_UNIQUE = 0
    AND s.INDEX_NAME <> 'PRIMARY'
  GROUP BY s.INDEX_NAME
  HAVING COUNT(*) = 1 AND MAX(s.COLUMN_NAME) = 'user_id'
  LIMIT 1
);
SET @drop_uk_sql := IF(@uk_user_id IS NULL, 'DO 0',
  CONCAT('ALTER TABLE `refresh_tokens` DROP INDEX `', @uk_user_id, '`'));
PREPARE drop_uk_stmt FROM @drop_uk_sql;
EXECUTE drop_uk_stmt;
DEALLOCATE PREPARE drop_uk_stmt;

-- 3) 재발급 조회용 + 같은 토큰이 두 행에 들어가지 않게 token_hash UNIQUE
ALTER TABLE `refresh_tokens` ADD UNIQUE KEY `uk_refresh_tokens_token_hash` (`token_hash`);
