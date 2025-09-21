package com.project.byeoldori.user.entity

import com.project.byeoldori.common.jpa.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "password_reset_tokens")
data class PasswordResetToken(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(15),

    var usedAt: LocalDateTime? = null
) : BaseTimeEntity() {
    fun isUsable(now: LocalDateTime = LocalDateTime.now()) =
        usedAt == null && now.isBefore(expiresAt)
}

