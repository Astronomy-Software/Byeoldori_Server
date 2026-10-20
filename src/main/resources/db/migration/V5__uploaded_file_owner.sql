-- V5: 업로드 파일의 업로더 기록(uploaded_file).
-- 게시글·일정 imageUrls 는 클라이언트가 보내는 문자열이라, 소유자 확인 없이 물리 삭제하면
-- 남의 공개 파일 URL 을 붙였다 지우는 것만으로 그 파일이 지워졌다(IDOR). 이제 물리 삭제는
-- 이 표에 요청자가 업로더로 기록된 파일만 한다. 사용자별 업로드 쿼터(시간당 개수·하루 용량)도 이 표로 센다.
-- 엔티티: community/common/domain/UploadedFile.kt (ddl-auto=validate 이므로 컬럼·타입을 그대로 맞출 것)
CREATE TABLE `uploaded_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `url` varchar(512) NOT NULL,
  `uploader_id` bigint NOT NULL,
  `size_bytes` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uploaded_file_url` (`url`),
  KEY `idx_uploaded_file_uploader_created` (`uploader_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
