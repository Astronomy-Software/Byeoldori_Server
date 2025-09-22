package com.project.byeoldori.community.like.repository

import com.project.byeoldori.community.like.domain.LikeEntity
import com.project.byeoldori.community.like.domain.LikeId
import org.springframework.data.jpa.repository.JpaRepository

interface LikeRepository : JpaRepository<LikeEntity, LikeId> {
    fun existsByPostIdAndUserId(postId: Long, userId: Long): Boolean
    fun findByPostIdAndUserId(postId: Long, userId: Long): LikeEntity?
    fun deleteByPostIdAndUserId(postId: Long, userId: Long)
    fun countByPostId(postId: Long): Long
}