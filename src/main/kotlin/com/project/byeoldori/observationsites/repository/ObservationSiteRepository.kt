package com.project.byeoldori.observationsites.repository

import com.project.byeoldori.observationsites.entity.ObservationSite
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ObservationSiteRepository : JpaRepository<ObservationSite, Long> {
    fun findByName(name: String): ObservationSite?

    // FULLTEXT 검색 (V5 마이그레이션 이후)
    @Query(
        value = "SELECT * FROM observation_site WHERE MATCH(name) AGAINST (:keyword IN BOOLEAN MODE)",
        nativeQuery = true
    )
    fun searchByName(@Param("keyword") keyword: String): List<ObservationSite>

    // 기존 LIKE 방식 유지 (fallback 또는 짧은 키워드 대응)
    fun findByNameContaining(keyword: String): List<ObservationSite>
    fun existsByName(name: String): Boolean
    fun existsByNameAndLatitudeAndLongitude(name: String, latitude: Double, longitude: Double): Boolean
}