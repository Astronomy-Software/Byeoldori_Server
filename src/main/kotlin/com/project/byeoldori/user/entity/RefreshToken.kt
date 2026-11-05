package com.project.byeoldori.user.entity

import com.project.byeoldori.common.jpa.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDateTime

// 로그인(기기·브라우저)마다 한 행. 재발급은 tokenHash 로 그 행만 회전시킨다.
// V6__refresh_tokens_multi_session.sql 과 인덱스·제약 이름을 맞춘다.
@Entity
@Table(
    name = "refresh_tokens",
    indexes = [Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")],
    uniqueConstraints = [UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = ["token_hash"])]
)
class RefreshToken(
    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
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
