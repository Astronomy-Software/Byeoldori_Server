package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.ReviewPost
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewPostRepository : JpaRepository<ReviewPost, Long>