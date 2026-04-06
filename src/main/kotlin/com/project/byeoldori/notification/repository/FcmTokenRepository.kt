package com.project.byeoldori.notification.repository

import com.project.byeoldori.notification.entity.FcmToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FcmTokenRepository : JpaRepository<FcmToken, Long> {
    @Query("SELECT f.token FROM FcmToken f WHERE f.user.id = :userId")
    fun findTokensByUserId(userId: Long): List<String>

    fun existsByToken(token: String): Boolean
    fun deleteByUserIdAndToken(userId: Long, token: String)
    fun deleteAllByUserId(userId: Long)
}
