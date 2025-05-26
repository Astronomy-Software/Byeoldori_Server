package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.RefreshToken
import io.lettuce.core.dynamic.annotation.Param
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface RefreshTokenRepository : JpaRepository<RefreshToken, String> {
    fun findByToken(token: String): RefreshToken?

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.email = :email")
    fun deleteByEmail(@Param("email") email: String)
}