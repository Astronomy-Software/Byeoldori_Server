-- V5: 커뮤니티 게시글 제목/내용 및 관측지 이름에 FULLTEXT 인덱스 추가
-- 기존 LIKE '%keyword%' 방식(인덱스 미사용)을 MATCH-AGAINST 방식으로 대체
ALTER TABLE community ADD FULLTEXT INDEX ft_community_title (title);
ALTER TABLE community ADD FULLTEXT INDEX ft_community_content (content);
ALTER TABLE observation_site ADD FULLTEXT INDEX ft_site_name (name);
