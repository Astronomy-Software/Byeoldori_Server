package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.ReviewPost
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReviewPostRepository : JpaRepository<ReviewPost, Long> {
    fun countByObservationSiteId(observationSiteId: Long): Long

    @Query("SELECT ROUND(AVG(r.score), 1) FROM ReviewPost r WHERE r.observationSite.id = :siteId AND r.score IS NOT NULL")
    fun findAverageScoreBySiteId(@Param("siteId") siteId: Long): Double?

    @Query("SELECT r.id, r.observationSite.id FROM ReviewPost r WHERE r.id IN :postIds AND r.observationSite.id IS NOT NULL")
    fun findObservationSiteIdsByPostIds(@Param("postIds") postIds: List<Long>): List<Array<Any>>

    @Query("""
        SELECT r.observationSite.id, ROUND(AVG(r.score), 1)
        FROM ReviewPost r
        WHERE r.observationSite.id IN :siteIds AND r.score IS NOT NULL
        GROUP BY r.observationSite.id
    """)
    fun findAverageScoresBySiteIds(@Param("siteIds") siteIds: List<Long>): List<Array<Any>>

    // 관측지가 삭제될 때 후기와의 연결을 끊는다(FK 에 ON DELETE 가 없어 그대로 지우면 삭제가 실패한다).
    // 장소 정보가 사라지지 않도록 location 이 비어 있으면 관측지 이름을 남긴다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ReviewPost r
        set r.location = coalesce(r.location, :siteName), r.observationSite = null
        where r.observationSite.id = :siteId
    """)
    fun detachObservationSite(@Param("siteId") siteId: Long, @Param("siteName") siteName: String): Int
}
