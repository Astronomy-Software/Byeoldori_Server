package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.EmailVerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, String> {

    fun findByIdAndUsedAtIsNull(id: String): Optional<EmailVerificationToken>

    @Modifying
    @Query("delete from EmailVerificationToken t where t.user.id = :userId")
    fun deleteAllByUserId(userId: Long)
}