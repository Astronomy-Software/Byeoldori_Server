package com.project.byeoldori.observationsites.repository

import com.project.byeoldori.observationsites.entity.ObservationSite
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository

@Transactional
interface ObservationSiteRepository : JpaRepository<ObservationSite, Long> {
    fun findByName(name: String): ObservationSite?
    fun findByNameContaining(keyword: String): List<ObservationSite>
    fun deleteByName(name: String)
    fun existsByName(name: String): Boolean
    fun existsByNameAndLatitudeAndLongitude(name: String, latitude: Double, longitude: Double): Boolean

}
