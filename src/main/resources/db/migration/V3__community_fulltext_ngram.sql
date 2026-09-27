-- V3: 커뮤니티 제목·본문 검색용 FULLTEXT 인덱스.
-- CommunityPostRepository 의 MATCH(title|content) AGAINST(... IN BOOLEAN MODE) 는 FULLTEXT 인덱스가
-- 없으면 MySQL 이 오류(ER_FT_MATCHING_KEY_NOT_FOUND)를 낸다. 운영에도 인덱스가 없어 키워드 검색이 실패했다.
-- 한국어는 공백 분리가 맞지 않아 ngram 파서(기본 토큰 2글자)를 쓴다.
-- InnoDB 는 FULLTEXT 인덱스를 한 번에 하나만 만들 수 있어(오류 1795) 문장을 나눈다.
ALTER TABLE `community` ADD FULLTEXT INDEX `ft_community_title` (`title`) WITH PARSER ngram;
ALTER TABLE `community` ADD FULLTEXT INDEX `ft_community_content` (`content`) WITH PARSER ngram;
