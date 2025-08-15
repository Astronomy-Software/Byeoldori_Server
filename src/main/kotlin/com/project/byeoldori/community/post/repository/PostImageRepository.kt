package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.PostImage
import org.springframework.data.jpa.repository.JpaRepository

interface PostImageRepository : JpaRepository<PostImage, Long> {
    fun findAllByPostIdOrderBySortOrderAsc(postId: Long): List<PostImage>
}