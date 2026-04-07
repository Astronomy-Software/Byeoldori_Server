package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityPostRepository : JpaRepository<CommunityPost, Long> {
    fun findAllByType(type: PostType, pageable: Pageable): Page<CommunityPost>

    // FULLTEXT 검색 (MATCH-AGAINST, Boolean Mode) — native query는 Sort 미지원, ORDER BY 직접 명시
    @Query(
        value = "SELECT * FROM community WHERE type = :type AND MATCH(title) AGAINST (:keyword IN BOOLEAN MODE) ORDER BY created_at DESC",
        countQuery = "SELECT COUNT(*) FROM community WHERE type = :type AND MATCH(title) AGAINST (:keyword IN BOOLEAN MODE)",
        nativeQuery = true
    )
    fun searchByTitle(@Param("type") type: String, @Param("keyword") keyword: String, pageable: Pageable): Page<CommunityPost>

    @Query(
        value = "SELECT * FROM community WHERE type = :type AND MATCH(content) AGAINST (:keyword IN BOOLEAN MODE) ORDER BY created_at DESC",
        countQuery = "SELECT COUNT(*) FROM community WHERE type = :type AND MATCH(content) AGAINST (:keyword IN BOOLEAN MODE)",
        nativeQuery = true
    )
    fun searchByContent(@Param("type") type: String, @Param("keyword") keyword: String, pageable: Pageable): Page<CommunityPost>

    // 닉네임 검색은 FULLTEXT 부적합 (짧은 값) → 기존 LIKE 유지
    fun findByTypeAndAuthorNicknameContaining(type: PostType, nickname: String, pageable: Pageable): Page<CommunityPost>

    @Query("SELECT SUM(p.likeCount) FROM ReviewPost r JOIN r.post p WHERE r.observationSite.id = :siteId")
    fun sumLikesBySiteId(@Param("siteId") siteId: Long): Long?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommunityPost p set p.viewCount = p.viewCount + 1 where p.id = :postId")
    fun increaseViewCount(@Param("postId") postId: Long): Int
}