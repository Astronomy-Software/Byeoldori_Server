package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.EducationPost
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface EducationPostRepository : JpaRepository<EducationPost, Long>{
    @EntityGraph(attributePaths = ["post"])
    fun findAllByStarObjectName(starObjectName: String, sort: Sort): List<EducationPost>
}