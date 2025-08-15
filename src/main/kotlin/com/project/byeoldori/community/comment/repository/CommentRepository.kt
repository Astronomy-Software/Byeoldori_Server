package com.project.byeoldori.community.comment.repository

import com.project.byeoldori.community.comment.domain.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CommentRepository : JpaRepository<Comment, Long> {
    fun countByPostIdAndDeletedFalse(postId: Long): Long
    fun findByPostId(postId: Long, pageable: Pageable): Page<Comment>
}
