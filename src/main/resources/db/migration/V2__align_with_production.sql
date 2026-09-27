-- V2: 운영 DB(2026-09-27 mysqldump --no-data)와 V1 baseline 의 차이를 메운다.
-- 운영은 그동안 Hibernate ddl-auto=update + 수동 ALTER 로 이 상태가 됐다.
-- 운영 DB 는 spring.flyway.baseline-version=2 로 "이미 V2 상태"로 기록되므로 이 파일은
-- 새(빈) DB 에서만 실행된다 → 새 DB 도 V1→V2 로 운영과 같은 구조가 된다.

-- 1) 시스템 설정(검수 토글·1회 이관 완료 표시 등) 키-값 테이블
CREATE TABLE `system_config` (
  `config_key` varchar(128) NOT NULL,
  `config_value` varchar(1024) NOT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 게시글 본문: TINYTEXT(255바이트)로는 한글 약 85자에서 저장 실패 → LONGTEXT
ALTER TABLE `community` MODIFY `content` longtext NOT NULL;

-- 3) 댓글 255자 → 1000자
ALTER TABLE `comment` MODIFY `content` varchar(1000) NOT NULL;

-- 4) 교육 게시글 ↔ 교육 프로그램(MongoDB) 연결 id
ALTER TABLE `education_post` ADD COLUMN `program_id` varchar(64) DEFAULT NULL AFTER `tags`;
