package com.project.byeoldori.community.comment.repository

import com.project.byeoldori.community.comment.domain.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommentRepository : JpaRepository<Comment, Long> {
    fun countByPostIdAndDeletedFalse(postId: Long): Long

    // 목록 렌더링 시 author 접근으로 인한 N+1 제거 (author를 함께 로드)
    @EntityGraph(attributePaths = ["author"])
    fun findByPostId(postId: Long, pageable: Pageable): Page<Comment>

    // 댓글 좋아요 수는 원자적 증감으로만 바꾼다(CommunityPostRepository.addLikeCount 와 같은 이유)
    @Modifying(flushAutomatically = true)
    @Query("update Comment c set c.likeCount = c.likeCount + :delta where c.id = :commentId and c.likeCount + :delta >= 0")
    fun addLikeCount(@Param("commentId") commentId: Long, @Param("delta") delta: Long): Int

    @Query("select c.likeCount from Comment c where c.id = :commentId")
    fun findLikeCountById(@Param("commentId") commentId: Long): Long?
}
