package com.project.byeoldori.community.comment.repository

import com.project.byeoldori.community.comment.domain.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface CommentRepository : JpaRepository<Comment, Long> {
    fun countByPostIdAndDeletedFalse(postId: Long): Long

    // 목록 렌더링 시 author 접근으로 인한 N+1 제거 (author를 함께 로드)
    @EntityGraph(attributePaths = ["author"])
    fun findByPostId(postId: Long, pageable: Pageable): Page<Comment>
}
