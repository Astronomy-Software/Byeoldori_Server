package com.project.byeoldori.user.entity

import com.project.byeoldori.common.jpa.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

// 이메일 인증을 위한 토큰 정보를 저장하는 엔티티입니다.
@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(

    @Id
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY) // 한 유저에게 여러 토큰(재발송) 가능
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusHours(24),

    var usedAt: LocalDateTime? = null
) : BaseTimeEntity() {
    fun isUsable(now: LocalDateTime = LocalDateTime.now()) =
        usedAt == null && now.isBefore(expiresAt)
}