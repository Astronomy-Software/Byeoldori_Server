package com.project.byeoldori.observationsites.repository

import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserSavedSiteRepository : JpaRepository<UserSavedSite, Long> {
    fun findAllByUser(user: User): List<UserSavedSite>

    fun existsByUserAndSite(user: User, site: ObservationSite): Boolean

    fun findByIdAndUser(id: Long, user: User): Optional<UserSavedSite>

    fun findByUserAndSite(user: User, site: ObservationSite): Optional<UserSavedSite>

    fun findByUserAndCustomLatitudeAndCustomLongitude(user: User, latitude: Double, longitude: Double): Optional<UserSavedSite>

    fun deleteAllBySite_Id(siteId: Long)
}