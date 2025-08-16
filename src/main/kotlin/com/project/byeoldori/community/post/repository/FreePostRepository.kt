package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.FreePost
import org.springframework.data.jpa.repository.JpaRepository

interface FreePostRepository : JpaRepository<FreePost, Long>
