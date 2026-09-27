package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.EducationRating
import com.project.byeoldori.community.post.domain.EducationRatingId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EducationRatingRepository : JpaRepository<EducationRating, EducationRatingId> {
    @Query("SELECT COALESCE(ROUND(AVG(er.score), 1), 0.0) FROM EducationRating er WHERE er.educationPost.id = :postId")
    fun findAverageScoreByPostId(@Param("postId") postId: Long): Double

    // education_rating → education_post FK 에 ON DELETE CASCADE 가 없어, 게시글보다 먼저 지워야 삭제가 된다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EducationRating er where er.educationPost.id = :postId")
    fun deleteAllByPostId(@Param("postId") postId: Long): Int
}