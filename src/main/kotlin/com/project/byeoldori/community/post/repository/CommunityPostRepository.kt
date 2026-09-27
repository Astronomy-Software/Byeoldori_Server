package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityPostRepository : JpaRepository<CommunityPost, Long> {
    // 목록 렌더링 시 author 접근으로 인한 N+1 제거 (author를 함께 로드)
    @EntityGraph(attributePaths = ["author"])
    fun findAllByType(type: PostType, pageable: Pageable): Page<CommunityPost>

    // 공개 목록용 — 교육 게시글은 발행(PUBLISHED)된 것만. 작성 중(DRAFT)은 작성자 외에 보이면 안 된다.
    @EntityGraph(attributePaths = ["author"])
    @Query(
        value = "select p from CommunityPost p where p.type = :type and (p.type <> com.project.byeoldori.community.common.domain.PostType.EDUCATION or exists (select 1 from EducationPost e where e.post = p and e.status = com.project.byeoldori.community.common.domain.EducationStatus.PUBLISHED))",
        countQuery = "select count(p) from CommunityPost p where p.type = :type and (p.type <> com.project.byeoldori.community.common.domain.PostType.EDUCATION or exists (select 1 from EducationPost e where e.post = p and e.status = com.project.byeoldori.community.common.domain.EducationStatus.PUBLISHED))"
    )
    fun findVisibleByType(@Param("type") type: PostType, pageable: Pageable): Page<CommunityPost>

    // FULLTEXT 검색 (MATCH-AGAINST, Boolean Mode) — native query는 Sort 미지원, ORDER BY 직접 명시
    @Query(
        value = "SELECT * FROM community WHERE type = :type AND MATCH(title) AGAINST (:keyword IN BOOLEAN MODE) AND (type <> 'EDUCATION' OR EXISTS (SELECT 1 FROM education_post e WHERE e.post_id = community.id AND e.status = 'PUBLISHED')) ORDER BY created_at DESC",
        countQuery = "SELECT COUNT(*) FROM community WHERE type = :type AND MATCH(title) AGAINST (:keyword IN BOOLEAN MODE) AND (type <> 'EDUCATION' OR EXISTS (SELECT 1 FROM education_post e WHERE e.post_id = community.id AND e.status = 'PUBLISHED'))",
        nativeQuery = true
    )
    fun searchByTitle(@Param("type") type: String, @Param("keyword") keyword: String, pageable: Pageable): Page<CommunityPost>

    @Query(
        value = "SELECT * FROM community WHERE type = :type AND MATCH(content) AGAINST (:keyword IN BOOLEAN MODE) AND (type <> 'EDUCATION' OR EXISTS (SELECT 1 FROM education_post e WHERE e.post_id = community.id AND e.status = 'PUBLISHED')) ORDER BY created_at DESC",
        countQuery = "SELECT COUNT(*) FROM community WHERE type = :type AND MATCH(content) AGAINST (:keyword IN BOOLEAN MODE) AND (type <> 'EDUCATION' OR EXISTS (SELECT 1 FROM education_post e WHERE e.post_id = community.id AND e.status = 'PUBLISHED'))",
        nativeQuery = true
    )
    fun searchByContent(@Param("type") type: String, @Param("keyword") keyword: String, pageable: Pageable): Page<CommunityPost>

    // 닉네임 검색은 FULLTEXT 부적합 (짧은 값) → 기존 LIKE 유지
    @EntityGraph(attributePaths = ["author"])
    @Query(
        value = "select p from CommunityPost p where p.type = :type and p.author.nickname like concat('%', :nickname, '%') and (p.type <> com.project.byeoldori.community.common.domain.PostType.EDUCATION or exists (select 1 from EducationPost e where e.post = p and e.status = com.project.byeoldori.community.common.domain.EducationStatus.PUBLISHED))",
        countQuery = "select count(p) from CommunityPost p where p.type = :type and p.author.nickname like concat('%', :nickname, '%') and (p.type <> com.project.byeoldori.community.common.domain.PostType.EDUCATION or exists (select 1 from EducationPost e where e.post = p and e.status = com.project.byeoldori.community.common.domain.EducationStatus.PUBLISHED))"
    )
    fun findVisibleByTypeAndAuthorNickname(@Param("type") type: PostType, @Param("nickname") nickname: String, pageable: Pageable): Page<CommunityPost>

    @Query("SELECT SUM(p.likeCount) FROM ReviewPost r JOIN r.post p WHERE r.observationSite.id = :siteId")
    fun sumLikesBySiteId(@Param("siteId") siteId: Long): Long?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommunityPost p set p.viewCount = p.viewCount + 1 where p.id = :postId")
    fun increaseViewCount(@Param("postId") postId: Long): Int
}