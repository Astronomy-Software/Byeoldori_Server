package com.project.byeoldori.observationsites.repository

import com.project.byeoldori.observationsites.entity.UserSavedSite
import org.springframework.data.jpa.repository.JpaRepository

interface UserSavedSiteRepository : JpaRepository<UserSavedSite, Long> {
    fun existsByUserIdAndSiteId(userId: String, siteId: Long): Boolean
    fun findByUserId(userId: String): List<UserSavedSite>
    fun deleteByUserIdAndSiteId(userId: String, siteId: Long)
    fun deleteBySiteId(siteId: Long)
}