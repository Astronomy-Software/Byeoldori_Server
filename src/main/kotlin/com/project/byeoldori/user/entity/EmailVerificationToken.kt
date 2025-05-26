package com.project.byeoldori.user.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

// 이메일 인증을 위한 토큰 정보를 저장하는 엔티티입니다.
@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(

    @Id
    val token: String = UUID.randomUUID().toString(),  // 토큰은 UUID로 생성

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,  // 토큰과 연결된 사용자

    @Column(nullable = false)
    val expiresAt: LocalDateTime = LocalDateTime.now().plusHours(1),  // 유효기간 1시간

    @Column(nullable = false)
    var verified: Boolean = false,  // 인증 성공 여부

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)