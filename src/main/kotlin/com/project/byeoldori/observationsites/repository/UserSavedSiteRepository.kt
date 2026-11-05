package com.project.byeoldori.observationsites.repository

import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface UserSavedSiteRepository : JpaRepository<UserSavedSite, Long> {
    fun findAllByUser(user: User): List<UserSavedSite>

    fun existsByUserAndSite(user: User, site: ObservationSite): Boolean

    fun findByIdAndUser(id: Long, user: User): Optional<UserSavedSite>

    fun findByUserAndSite(user: User, site: ObservationSite): Optional<UserSavedSite>

    fun findByUserAndCustomLatitudeAndCustomLongitude(user: User, latitude: Double, longitude: Double): Optional<UserSavedSite>

    fun deleteAllBySite_Id(siteId: Long)

    // 회원 탈퇴 시 즐겨찾기(직접 지정한 좌표·이름 포함)를 지운다
    @Modifying(flushAutomatically = true)
    @Query("delete from UserSavedSite s where s.user.id = :userId")
    fun deleteAllByUserId(@Param("userId") userId: Long): Int
}