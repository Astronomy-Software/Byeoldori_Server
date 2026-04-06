package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, String> {
    fun findByIdAndUsedAtIsNull(id: String): Optional<PasswordResetToken>
    fun deleteAllByUserId(userId: Long)
}
