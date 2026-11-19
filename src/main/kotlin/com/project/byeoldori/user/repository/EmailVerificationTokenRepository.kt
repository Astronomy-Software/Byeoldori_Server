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

    // 최근(만료 시각 기준으로 역산) 발급한 미사용 링크가 있는지. 토큰은 발급 시각 + 24시간으로 만료되므로
    // expiresAt > :threshold(= 지금 + 24시간 - 재발송 간격) 이면 그 간격 안에 발급된 것이다. 재발송 연타 방지용
    @Query("select count(t) > 0 from EmailVerificationToken t where t.user.id = :userId and t.usedAt is null and t.expiresAt > :threshold")
    fun existsUnusedExpiringAfter(userId: Long, threshold: LocalDateTime): Boolean

    @Modifying
    @Query("delete from EmailVerificationToken t where t.user.id = :userId")
    fun deleteAllByUserId(userId: Long)
}