package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.EmailVerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, String> {

    fun findByIdAndUsedAtIsNull(id: String): Optional<EmailVerificationToken>

    // 아직 쓸 수 있는(미사용·미만료) 인증 링크가 남아 있는지
    @Query("select count(t) > 0 from EmailVerificationToken t where t.user.id = :userId and t.usedAt is null and t.expiresAt > :now")
    fun existsUsableByUserId(userId: Long, now: LocalDateTime): Boolean

    @Modifying
    @Query("delete from EmailVerificationToken t where t.user.id = :userId")
    fun deleteAllByUserId(userId: Long)
}