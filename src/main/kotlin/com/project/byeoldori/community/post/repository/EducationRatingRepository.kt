package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.EducationRating
import com.project.byeoldori.community.post.domain.EducationRatingId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EducationRatingRepository : JpaRepository<EducationRating, EducationRatingId> {
    @Query("SELECT COALESCE(ROUND(AVG(er.score), 1), 0.0) FROM EducationRating er WHERE er.educationPost.id = :postId")
    fun findAverageScoreByPostId(@Param("postId") postId: Long): Double
}