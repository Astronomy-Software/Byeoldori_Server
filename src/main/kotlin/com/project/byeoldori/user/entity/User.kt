package com.project.byeoldori.user.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,  // 사용자 고유 ID (자동 생성)

    @Column(nullable = false, unique = true)
    val email: String,  // 사용자 이메일 (중복 불가)

    @Column(nullable = false)
    var password: String,  // 암호화된 비밀번호

    @Column(nullable = true, unique = true)
    var nickname: String? = null,  // 사용자 닉네임 (중복 불가)

    @Column(nullable = true)
    var birthdate: LocalDate? = null, // 생년월일

    @Column(nullable = false)
    val termsOfService: Boolean,  // 서비스 이용 약관 동의 (필수)

    @Column(nullable = false)
    val privacyPolicy: Boolean,   // 개인정보 수집 및 이용 동의 (필수)

    val marketing: Boolean = false,  // 마케팅 수신 동의 (선택)

    val location: Boolean = false,   // 위치 정보 이용 동의 (선택)

    // 이메일 인증 완료 여부 (기본값: false)
    @Column(nullable = false)
    var emailVerified: Boolean = false,

    // 생성일자 (가입일자)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    // 수정일자 (정보 수정 시 갱신)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}