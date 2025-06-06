package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.EmailVerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, String> {

    // 토큰 값으로 엔티티 조회
    fun findByToken(token: String): Optional<EmailVerificationToken>

    // 특정 사용자의 토큰을 삭제 (재가입 또는 중복 방지 목적)
    fun deleteByUserId(userId: Long)
    fun deleteAllByUserEmail(email: String)
}