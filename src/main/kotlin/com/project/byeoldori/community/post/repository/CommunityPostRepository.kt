package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType

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

    // 한 글자 검색어용 LIKE 대체(ngram FULLTEXT 는 2글자 미만을 못 찾음). pattern 은 SearchKeyword 가 이스케이프한 값
    @EntityGraph(attributePaths = ["author"])
    @Query(
        value = "select p from CommunityPost p where p.type = :type and p.title like concat('%', :pattern, '%') escape '!' and (p.type <> com.project.byeoldori.community.common.domain.PostType.EDUCATION or exists (select 1 from EducationPost e where e.post = p and e.status = com.project.byeoldori.community.common.domain.EducationStatus.PUBLISHED))",
        countQuery = "select count(p) from CommunityPost p where p.type = :type and p.title like concat('%', :pattern, '%') escape '!' and (p.type <> com.project.byeoldori.community.common.domain.PostType.EDUCATION or exists (select 1 from EducationPost e where e.post = p and e.status = com.project.byeoldori.community.common.domain.EducationStatus.PUBLISHED))"
    )
    fun findVisibleByTypeAndTitleLike(@Param("type") type: PostType, @Param("pattern") pattern: String, pageable: Pageable): Page<CommunityPost>

    @EntityGraph(attributePaths = ["author"])
    @Query(
        value = "select p from CommunityPost p where p.type = :type and p.content like concat('%', :pattern, '%') escape '!' and (p.type <> com.project.byeoldori.community.common.domain.PostType.EDUCATION or exists (select 1 from EducationPost e where e.post = p and e.status = com.project.byeoldori.community.common.domain.EducationStatus.PUBLISHED))",
        countQuery = "select count(p) from CommunityPost p where p.type = :type and p.content like concat('%', :pattern, '%') escape '!' and (p.type <> com.project.byeoldori.community.common.domain.PostType.EDUCATION or exists (select 1 from EducationPost e where e.post = p and e.status = com.project.byeoldori.community.common.domain.EducationStatus.PUBLISHED))"
    )
    fun findVisibleByTypeAndContentLike(@Param("type") type: PostType, @Param("pattern") pattern: String, pageable: Pageable): Page<CommunityPost>

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

    // 댓글·좋아요 수도 조회수처럼 원자적 증감으로만 바꾼다. 엔티티 필드를 고쳐 flush 하면
    // 트랜잭션 시작 때 읽은 값으로 행 전체를 다시 써서 동시에 올라간 다른 카운터를 덮어쓴다.
    // 0 아래로는 내려가지 않는다(이미 0 이면 0 행 갱신).
    @Modifying(flushAutomatically = true)
    @Query("update CommunityPost p set p.commentCount = p.commentCount + :delta where p.id = :postId and p.commentCount + :delta >= 0")
    fun addCommentCount(@Param("postId") postId: Long, @Param("delta") delta: Long): Int

    @Modifying(flushAutomatically = true)
    @Query("update CommunityPost p set p.likeCount = p.likeCount + :delta where p.id = :postId and p.likeCount + :delta >= 0")
    fun addLikeCount(@Param("postId") postId: Long, @Param("delta") delta: Long): Int

    // 좋아요·댓글 INSERT 는 FK 검사로 부모 게시글 행에 S 락을 잡고, 이어지는 카운터 UPDATE 는 X 락이 필요하다.
    // 서로 다른 사용자 두 명이 동시에 누르면 S 락을 쥔 채 서로의 X 락을 기다려 데드락(500)이 났다.
    // 트랜잭션 맨 앞에서 부모 행을 X 락(SELECT ... FOR UPDATE)으로 먼저 잡아 순서를 고정한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CommunityPost p where p.id = :postId")
    fun findByIdForUpdate(@Param("postId") postId: Long): CommunityPost?

    // 영속성 컨텍스트의 엔티티가 아닌 DB 의 현재 값(방금 원자적으로 바꾼 값 포함)을 읽는다
    @Query("select p.likeCount from CommunityPost p where p.id = :postId")
    fun findLikeCountById(@Param("postId") postId: Long): Long?
}