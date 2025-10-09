package com.project.byeoldori.community.like.repository

import com.project.byeoldori.community.like.domain.CommentLike
import com.project.byeoldori.community.like.domain.CommentLikeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommentLikeRepository : JpaRepository<CommentLike, CommentLikeId> {
    fun existsByCommentIdAndUserId(commentId: Long, userId: Long): Boolean
    fun deleteByCommentIdAndUserId(commentId: Long, userId: Long)
    fun countByCommentId(commentId: Long): Long

    @Query("SELECT cl.comment.id FROM CommentLike cl WHERE cl.user.id = :userId AND cl.comment.id IN :commentIds")
    fun findLikedCommentIds(@Param("userId") userId: Long, @Param("commentIds") commentIds: List<Long>): List<Long>
}