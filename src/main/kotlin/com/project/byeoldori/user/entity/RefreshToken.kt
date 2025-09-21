package com.project.byeoldori.user.entity

import com.project.byeoldori.common.jpa.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(nullable = false, length = 64) // SHA-256 hex length
    var tokenHash: String,

    @Column(nullable = false)
    var expiresAt: LocalDateTime,

    var revokedAt: LocalDateTime? = null,
    var rotatedAt: LocalDateTime? = null
) : BaseTimeEntity() {
    fun isActive(now: LocalDateTime = LocalDateTime.now()) =
        revokedAt == null && now.isBefore(expiresAt)
}