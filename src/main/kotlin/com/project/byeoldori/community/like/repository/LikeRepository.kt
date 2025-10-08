package com.project.byeoldori.community.like.repository

import com.project.byeoldori.community.like.domain.LikeEntity
import com.project.byeoldori.community.like.domain.LikeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface LikeRepository : JpaRepository<LikeEntity, LikeId> {
    fun existsByPostIdAndUserId(postId: Long, userId: Long): Boolean
    fun deleteByPostIdAndUserId(postId: Long, userId: Long)
    fun countByPostId(postId: Long): Long

    @Query("SELECT l.post.id FROM LikeEntity l WHERE l.user.id = :userId AND l.post.id IN :postIds")
    fun findLikedPostIds(userId: Long, postIds: Collection<Long>): List<Long>
}