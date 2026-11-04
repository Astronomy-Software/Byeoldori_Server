-- V8: 목록·예보 조회 인덱스 추가 + 커뮤니티 FULLTEXT(ngram) 재색인.
-- 엔티티: community/post/domain/CommunityPost.kt, forecast/entity/MidCombinedForecast.kt (인덱스 이름·컬럼을 그대로 맞출 것)

-- 1) 게시판·홈 목록은 모두 "where type = ? order by created_at desc limit N" 이다.
--    PK·author_id·FULLTEXT 만 있어 매번 타입 전체를 읽고 filesort 했다.
ALTER TABLE `community` ADD INDEX `idx_community_type_created` (`type`, `created_at`);

-- 2) 중기예보 조회(findBySiRegIdOrderByTmEfAscTmFcDesc)는 si_reg_id 로 거르는데,
--    유니크 키 (tm_fc, tm_ef, si_reg_id) 는 선두가 tm_fc 라 쓸 수 없어 전체를 읽었다.
ALTER TABLE `mid_combined_forecast` ADD INDEX `idx_mid_combined_si_reg_tm_fc` (`si_reg_id`, `tm_fc`);

-- 3) 제목·본문 FULLTEXT 재색인. ngram 파서는 불용어를 "포함한" 토큰을 색인에서 빼는데, 기본 InnoDB 불용어
--    목록에 'a'·'i'·'on'·'or' 등이 있어 'Orion'(or·ri·io·on) 같은 영문 제목은 토큰이 하나도 색인되지 않아
--    검색 결과가 항상 0건이었다. 불용어 사용 여부는 인덱스를 만들 때 적용되므로 지우고 다시 만든다.
--    전제: MySQL 의 GLOBAL innodb_ft_enable_stopword 가 OFF 여야 한다(세션 값만 바꾸면 효과 없음, 2026-10-23 확인).
--    배포가 앱 기동(이 마이그레이션) 전에 scripts/ensure-mysql-ft-stopword-off.sh 로 맞추고,
--    docker-compose.oci.yml 의 mysql command 에도 --innodb-ft-enable-stopword=0 을 둔다. 순서는 README 참고.
--    InnoDB 는 FULLTEXT 인덱스를 한 번에 하나만 만들 수 있어(오류 1795) 문장을 나눈다.
--    불용어 상태는 인덱스가 아니라 테이블 단위(FTS 캐시·CONFIG 의 use_stopword)로 남고, 마지막 FULLTEXT
--    인덱스가 지워질 때만 초기화된다. 하나라도 남긴 채 다시 만들면 예전 불용어 설정이 그대로 쓰이므로
--    두 인덱스를 모두 지운 뒤에 다시 만든다(순서를 바꾸지 말 것).
ALTER TABLE `community` DROP INDEX `ft_community_title`;
ALTER TABLE `community` DROP INDEX `ft_community_content`;
ALTER TABLE `community` ADD FULLTEXT INDEX `ft_community_title` (`title`) WITH PARSER ngram;
ALTER TABLE `community` ADD FULLTEXT INDEX `ft_community_content` (`content`) WITH PARSER ngram;
