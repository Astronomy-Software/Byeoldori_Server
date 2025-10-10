package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.ReviewPost
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewPostRepository : JpaRepository<ReviewPost, Long> {
    fun countByObservationSiteId(observationSiteId: Long): Long

    @Query("SELECT ROUND(AVG(r.score), 1) FROM ReviewPost r WHERE r.observationSite.id = :siteId AND r.score IS NOT NULL")
    fun findAverageScoreBySiteId(@Param("siteId") siteId: Long): Double?

    @Query("SELECT r.id, r.observationSite.id FROM ReviewPost r WHERE r.id IN :postIds AND r.observationSite.id IS NOT NULL")
    fun findObservationSiteIdsByPostIds(@Param("postIds") postIds: List<Long>): List<Array<Any>>
}