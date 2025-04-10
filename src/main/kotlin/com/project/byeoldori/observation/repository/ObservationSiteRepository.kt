package com.project.byeoldori.observation.repository

import com.project.byeoldori.observation.entity.ObservationSite
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository

@Transactional
interface ObservationSiteRepository : JpaRepository<ObservationSite, Long> {
    fun findByName(name: String): ObservationSite?
    fun findByNameContaining(keyword: String): List<ObservationSite>
    fun deleteByName(name: String)
    fun existsByName(name: String): Boolean
}
