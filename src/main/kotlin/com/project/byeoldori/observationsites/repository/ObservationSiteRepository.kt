package com.project.byeoldori.observationsites.repository

import com.project.byeoldori.observationsites.entity.ObservationSite
import org.springframework.data.jpa.repository.JpaRepository

interface ObservationSiteRepository : JpaRepository<ObservationSite, Long> {
    fun findByName(name: String): ObservationSite?
    fun findByNameContaining(keyword: String): List<ObservationSite>
    fun existsByName(name: String): Boolean
    fun existsByNameAndLatitudeAndLongitude(name: String, latitude: Double, longitude: Double): Boolean
}