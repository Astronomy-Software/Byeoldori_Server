package com.project.byeoldori.community.like.repository

import com.project.byeoldori.community.like.domain.CommentLike
import com.project.byeoldori.community.like.domain.CommentLikeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface CommentLikeRepository : JpaRepository<CommentLike, CommentLikeId> {
    fun existsByCommentIdAndUserId(commentId: Long, userId: Long): Boolean
    fun countByCommentId(commentId: Long): Long

    // 동시 요청(더블클릭)이 겹쳐도 PK 중복 500 이 나지 않게 INSERT IGNORE 로 넣는다. 실제로 넣었으면 1, 이미 있으면 0
    @Modifying
    @Query(
        value = "INSERT IGNORE INTO comment_likes (comment_id, user_id, created_at) VALUES (:commentId, :userId, :createdAt)",
        nativeQuery = true
    )
    fun insertIgnore(
        @Param("commentId") commentId: Long,
        @Param("userId") userId: Long,
        @Param("createdAt") createdAt: LocalDateTime
    ): Int

    // 벌크 삭제 — 이미 지워진 행이면 0 을 돌려줄 뿐 예외가 나지 않는다
    @Modifying
    @Query("delete from CommentLike cl where cl.comment.id = :commentId and cl.user.id = :userId")
    fun deleteByCommentIdAndUserId(@Param("commentId") commentId: Long, @Param("userId") userId: Long): Int

    @Query("SELECT cl.comment.id FROM CommentLike cl WHERE cl.user.id = :userId AND cl.comment.id IN :commentIds")
    fun findLikedCommentIds(@Param("userId") userId: Long, @Param("commentIds") commentIds: List<Long>): List<Long>
}