package com.project.byeoldori.repository

import com.project.byeoldori.user.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, String> {
    fun findByToken(token: String): Optional<PasswordResetToken>
}