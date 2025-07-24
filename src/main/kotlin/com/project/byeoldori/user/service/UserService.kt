package com.project.byeoldori.user.service

import com.project.byeoldori.repository.PasswordResetTokenRepository
import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.entity.*
import com.project.byeoldori.user.repository.*
import com.project.byeoldori.user.utils.PasswordValidator
import io.jsonwebtoken.Jwts
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val tokenRepository: EmailVerificationTokenRepository,
    private val emailService: EmailService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository
) {
    @Transactional
    fun signup(request: SignupRequestDto) {
        // 필수 약관 동의 확인
        if (!request.consents.termsOfService || !request.consents.privacyPolicy) {
            throw IllegalArgumentException("필수 약관에 동의해야 회원가입이 가능합니다.")
        }

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("이미 사용 중인 이메일입니다.")
        }

        // 비밀번호 & 비밀번호 확인 일치 여부 확인
        if (request.password != request.passwordConfirm) {
            throw IllegalArgumentException("비밀번호가 일치하지 않습니다.")
        }

        // 비밀번호 유효성 검사 (6자 이상, 영문+숫자 포함)
        if (!PasswordValidator.isValid(request.password)) {
            throw IllegalArgumentException("비밀번호는 6자 이상이며 영문자와 숫자를 포함해야 합니다.")
        }

        // 사용자 객체 생성 및 저장
        val user = User(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            name = request.name,
            phone = request.phone,
            termsOfService = request.consents.termsOfService,
            privacyPolicy = request.consents.privacyPolicy,
            marketing = request.consents.marketing,
            location = request.consents.location
        )
        userRepository.save(user)

        // 기존 토큰 삭제 (중복 방지)
        tokenRepository.deleteAllByUserEmail(user.email)

        // 토큰 생성 + 저장
        val token = EmailVerificationToken(user = user)
        tokenRepository.save(token)

        // 인증 메일 발송
        emailService.sendVerificationEmail(user.email, token.token)
    }

    @Transactional
    fun login(request: LoginRequestDto): Map<String, String> {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { IllegalArgumentException("이메일 또는 비밀번호가 잘못되었습니다.") }

        if (!user.emailVerified) {
            throw IllegalStateException("이메일 인증이 완료되지 않았습니다.")
        }

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw IllegalArgumentException("이메일 또는 비밀번호가 잘못되었습니다.")
        }

        refreshTokenRepository.deleteByEmail(user.email)

        // Access & Refresh Token 생성 (JWT 방식으로)
        val accessToken = jwtUtil.generateAccessToken(user.email)
        val refreshToken = jwtUtil.generateRefreshToken(user.email)

        // 만료 시간 계산 (JWT 자체에도 있지만 DB에도 기록)
        val claims = Jwts.parserBuilder()
            .setSigningKey(jwtUtil.getSecretKey())  // JwtUtil에 getSecretKey() 추가 필요
            .build()
            .parseClaimsJws(refreshToken)
            .body
        val expiresAt = claims.expiration.toInstant()
            .atZone(TimeZone.getDefault().toZoneId())
            .toLocalDateTime()

        // DB 저장
        val token = RefreshToken(
            email = user.email,
            token = refreshToken,
            expiresAt = expiresAt
        )
        refreshTokenRepository.save(token)

        return mapOf(
            "accessToken" to accessToken,
            "refreshToken" to refreshToken
        )
    }

    fun reissue(request: TokenReissueRequestDto): String {
        val savedToken = refreshTokenRepository.findByToken(request.refreshToken)
            ?: throw IllegalArgumentException("유효하지 않은 Refresh Token입니다.")

        if (savedToken.expiresAt.isBefore(LocalDateTime.now())) {
            throw IllegalStateException("Refresh Token이 만료되었습니다. 다시 로그인해주세요.")
        }

        return jwtUtil.generateAccessToken(savedToken.email)
    }

    @Transactional
    fun updateUserInfo(email: String, request: UserUpdateRequestDto) {
        val user: User = userRepository.findByEmail(email)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        if (request.nickname != null) {
            user.nickname = request.nickname
        }

        if (request.birthdate != null) {
            user.birthdate = request.birthdate
        }

        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)
    }

    fun findEmailByNameAndPhone(name: String, phone: String): String {
        val user = userRepository.findByNameAndPhone(name, phone)
            .orElseThrow { IllegalArgumentException("일치하는 사용자가 없습니다.") }
        return user.email
    }

    fun sendPasswordResetEmail(email: String) {
        if (!userRepository.existsByEmail(email)) {
            throw IllegalArgumentException("등록되지 않은 이메일입니다.")
        }

        val token = PasswordResetToken(email = email)
        passwordResetTokenRepository.save(token)

        val resetUrl = "http://localhost:8080/reset-password?token=${token.token}"
        emailService.sendEmail(
            to = email,
            subject = "[별도리] 비밀번호 재설정 안내",
            body = "아래 링크를 클릭하여 비밀번호를 재설정하세요:\n$resetUrl\n\n해당 링크는 10분간 유효합니다."
        )
    }

    fun resetPassword(token: String, newPassword: String) {
        val tokenEntity = passwordResetTokenRepository.findByToken(token)
            .orElseThrow { IllegalArgumentException("유효하지 않은 토큰입니다.") }

        if (tokenEntity.expiration.isBefore(LocalDateTime.now())) {
            throw IllegalArgumentException("토큰이 만료되었습니다.")
        }

        val user = userRepository.findByEmail(tokenEntity.email)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        user.password = passwordEncoder.encode(newPassword)
        userRepository.save(user)

        passwordResetTokenRepository.delete(tokenEntity)
    }

    fun logout(email: String) {
        refreshTokenRepository.deleteByEmail(email)
    }

    @Transactional
    fun deleteAccount(email: String) {
        // RefreshToken 삭제
        refreshTokenRepository.deleteByEmail(email)

        // 이메일 인증 토큰 삭제 (존재할 경우)
        tokenRepository.deleteAllByUserEmail(email)

        // 사용자 삭제
        val user = userRepository.findByEmail(email)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        userRepository.delete(user)
    }
}