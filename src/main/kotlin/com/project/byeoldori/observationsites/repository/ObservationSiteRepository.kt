package com.project.byeoldori.observationsites.repository

import com.project.byeoldori.observationsites.entity.ObservationSite
import org.springframework.data.jpa.repository.JpaRepository

interface ObservationSiteRepository : JpaRepository<ObservationSite, Long> {
    fun findByName(name: String): ObservationSite?

    // 이름 부분 일치 검색 (LIKE). FULLTEXT 인덱스가 없어 MATCH 쿼리는 제거했다.
    fun findByNameContaining(keyword: String): List<ObservationSite>
    fun existsByName(name: String): Boolean
    fun existsByNameAndLatitudeAndLongitude(name: String, latitude: Double, longitude: Double): Boolean
}