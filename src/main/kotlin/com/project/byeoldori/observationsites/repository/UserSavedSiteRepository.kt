package com.project.byeoldori.observationsites.repository

import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.observationsites.entity.UserSavedSiteId
import com.project.byeoldori.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserSavedSiteRepository : JpaRepository<UserSavedSite, UserSavedSiteId> {
    fun findAllByUser(user: User): List<UserSavedSite>
    fun existsByUserAndSite(user: User, site: ObservationSite): Boolean
    fun findByUserAndSite(user: User, site: ObservationSite): UserSavedSite?
    fun deleteBySiteId(siteId: Long)
}