-- V9: 즐겨찾기(user_saved_sites)에 같은 사용자가 같은 공식 관측지를 두 번 저장하지 못하게 UNIQUE 를 건다.
-- 토글 요청이 동시에 두 번 들어오면 둘 다 "없음"을 보고 INSERT 해 중복 행이 생길 수 있었고, 그 뒤로는
-- findByUserAndSite 가 결과 2건으로 실패(500)해 그 관측지를 지울 수도 다시 저장할 수도 없었다.
-- 엔티티: observationsites/entity/UserSavedSite.kt (제약 이름·컬럼을 그대로 맞출 것)
-- 임의 장소 즐겨찾기는 site_id 가 NULL 이라 UNIQUE 에 걸리지 않는다(NULL 끼리는 중복으로 보지 않음).
-- (H2 회귀 테스트 UserSavedSiteUniqueTest 가 이 파일을 그대로 실행하므로 표준 SQL 로만 쓴다)

-- 1) (user_id, site_id) 가 같은 행 중 가장 먼저 저장된 것(id 최소)만 남기고 지운다.
--    MySQL 은 DELETE 대상 테이블을 서브쿼리에서 바로 읽을 수 없어(오류 1093) 파생 테이블로 한 번 감싼다.
--    DISTINCT 는 옵티마이저가 파생 테이블을 바깥 쿼리에 합치지(merge) 못하게 해 같은 오류를 막는다.
DELETE FROM user_saved_sites
WHERE id IN (
    SELECT dup_id FROM (
        SELECT DISTINCT s.id AS dup_id
        FROM user_saved_sites s
        JOIN user_saved_sites o ON o.user_id = s.user_id AND o.site_id = s.site_id AND o.id < s.id
    ) dup
);

-- 2) 중복이 없어진 뒤 제약을 건다.
ALTER TABLE user_saved_sites ADD CONSTRAINT uk_user_saved_sites_user_site UNIQUE (user_id, site_id);
