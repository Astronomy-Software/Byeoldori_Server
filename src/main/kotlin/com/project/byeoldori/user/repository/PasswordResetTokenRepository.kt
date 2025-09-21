package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, String> {
    fun findByIdAndUsedAtIsNull(id: String): Optional<PasswordResetToken>
}