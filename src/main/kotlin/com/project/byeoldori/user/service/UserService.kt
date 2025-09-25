package com.project.byeoldori.user.service

import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.entity.EmailVerificationToken
import com.project.byeoldori.user.entity.PasswordResetToken
import com.project.byeoldori.user.entity.RefreshToken
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.*
import com.project.byeoldori.user.utils.PasswordValidator
import com.project.byeoldori.user.utils.TokenHasher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UserService(
    private val userRepository: UserRepository,
    private val emailTokenRepo: EmailVerificationTokenRepository,
    private val resetTokenRepo: PasswordResetTokenRepository,
    private val refreshTokenRepo: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwt: JwtUtil,
    private val emailService: EmailService
) {

    @Transactional
    fun signup(req: SignupRequestDto) {
        require(req.password == req.passwordConfirm) { "비밀번호가 일치하지 않습니다." }
        require(PasswordValidator.isValid(req.password)) { "비밀번호 정책을 확인하세요." }
        require(!userRepository.existsByEmail(req.email)) { "이미 사용 중인 이메일입니다." }
        if (req.nickname?.isNotBlank() == true) {
            require(!userRepository.existsByNickname(req.nickname)) { "이미 사용 중인 닉네임입니다." }
        }

        val user = userRepository.save(
            User(
                email = req.email,
                passwordHash = passwordEncoder.encode(req.password),
                name = req.name,
                phone = req.phone,
                nickname = req.nickname,
                birthdate = req.birthdate
            )
        )

        val token = emailTokenRepo.save(EmailVerificationToken(user = user))
        emailService.sendEmailVerification(user.email, token.id)
    }

    @Transactional
    fun verifyEmail(tokenId: String) {
        val token = emailTokenRepo.findByIdAndUsedAtIsNull(tokenId)
            .orElseThrow { IllegalArgumentException("토큰이 유효하지 않습니다.") }
        require(token.isUsable()) { "토큰이 만료되었습니다." }

        token.usedAt = LocalDateTime.now()
        token.user.emailVerified = true
    }

    @Transactional(readOnly = true)
    fun findEmailsByNameAndPhone(name: String, phone: String): List<String> {
        return userRepository.findAllByNameAndPhone(name, phone).map { it.email }
    }

    @Transactional
    fun login(req: LoginRequestDto): AuthResponseDto {
        val user = userRepository.findByEmail(req.email)
            .orElseThrow { IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.") }

        require(user.emailVerified) { "이메일 인증 후 로그인 가능합니다." }
        require(passwordEncoder.matches(req.password, user.passwordHash)) {
            "이메일 또는 비밀번호가 올바르지 않습니다."
        }

        val access = jwt.generateAccessToken(user.email)
        val refresh = jwt.generateRefreshToken(user.email)
        val hash = TokenHasher.sha256Hex(refresh)
        val exp = jwt.extractExpiration(refresh)

        val existing = refreshTokenRepo.findByUserIdForUpdate(user.id)
        if (existing.isPresent) {
            val t = existing.get()
            t.tokenHash = hash
            t.expiresAt  = exp
            t.revokedAt  = null
            t.rotatedAt  = null
        } else {
            refreshTokenRepo.save(RefreshToken(user = user, tokenHash = hash, expiresAt = exp))
        }

        user.lastLoginAt = LocalDateTime.now()
        val zone = java.time.ZoneId.systemDefault()
        return AuthResponseDto.of(
            access, refresh,
            jwt.extractExpiration(access).atZone(zone).toInstant(),
            jwt.extractExpiration(refresh).atZone(zone).toInstant()
        )
    }

    @Transactional
    fun reissue(req: TokenReissueRequestDto): AuthResponseDto {
        val email = jwt.extractEmail(req.refreshToken)
        val user = userRepository.findByEmail(email)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        val stored = refreshTokenRepo.findByUserIdForUpdate(user.id)
            .orElseThrow { IllegalArgumentException("다시 로그인 필요") }

        require(stored.isActive()) { "리프레시 토큰이 만료되었거나 폐기되었습니다." }
        require(stored.tokenHash == TokenHasher.sha256Hex(req.refreshToken)) { "리프레시 토큰이 일치하지 않습니다." }

        val newAccess  = jwt.generateAccessToken(email)
        val newRefresh = jwt.generateRefreshToken(email)
        stored.tokenHash = TokenHasher.sha256Hex(newRefresh)
        stored.expiresAt = jwt.extractExpiration(newRefresh)
        stored.rotatedAt = LocalDateTime.now()

        val zone = java.time.ZoneId.systemDefault()
        return AuthResponseDto.of(
            newAccess, newRefresh,
            jwt.extractExpiration(newAccess).atZone(zone).toInstant(),
            jwt.extractExpiration(newRefresh).atZone(zone).toInstant()
        )
    }

    @Transactional
    fun logout(authEmail: String) {
        val user = userRepository.findByEmail(authEmail).orElseThrow()
        refreshTokenRepo.deleteByUserId(user.id)
    }

    @Transactional
    fun requestPasswordReset(email: String, name: String, phone: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { IllegalArgumentException("입력하신 정보와 일치하는 계정을 찾을 수 없습니다.") }

        // 유틸 파일 없이, 서비스 내부에 인라인 정규화(숫자만 비교, +82 처리 옵션)
        fun normalizePhone(s: String?): String {
            if (s == null) return ""
            var d = s.filter { it.isDigit() }
            if (d.startsWith("82") && d.length >= 11) d = "0" + d.removePrefix("82")
            return d
        }

        val nameMatches = (user.name ?: "").trim().equals(name.trim(), ignoreCase = true)
        val phoneMatches = normalizePhone(user.phone) == normalizePhone(phone)

        if (!nameMatches || !phoneMatches) {
            throw IllegalArgumentException("입력하신 정보와 일치하는 계정을 찾을 수 없습니다.")
        }

        val token = resetTokenRepo.save(PasswordResetToken(user = user))

        emailService.sendPasswordReset(user.email, token.id)
    }

    @Transactional(readOnly = true)
    fun findVerifiedUserIdForPasswordReset(email: String, name: String, phone: String): Long? {
        val user = userRepository.findByEmail(email).orElse(null) ?: return null

        fun normalizePhone(s: String?): String {
            if (s == null) return ""
            var d = s.filter { it.isDigit() }
            if (d.startsWith("82") && d.length >= 11) d = "0" + d.removePrefix("82")
            return d
        }
        val nameMatches = (user.name ?: "").trim().equals(name.trim(), ignoreCase = true)
        val phoneMatches = normalizePhone(user.phone) == normalizePhone(phone)

        return if (nameMatches && phoneMatches) user.id else null
    }

    @Transactional
    fun resetPasswordByUserId(userId: Long, newRawPassword: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }

        user.passwordHash = passwordEncoder.encode(newRawPassword)

        refreshTokenRepo.deleteByUserId(user.id)
    }

    @Transactional(readOnly = true)
    fun getMe(email: String): UserMeResponseDto {
        val user = userRepository.findByEmail(email).orElseThrow()
        return UserMeResponseDto.from(user)
    }

    @Transactional
    fun updateMe(email: String, req: UserUpdateRequestDto) {
        val user = userRepository.findByEmail(email).orElseThrow()
        req.nickname?.let {
            if (it.isNotBlank() && it != user.nickname) {
                require(!userRepository.existsByNickname(it)) { "이미 사용 중인 닉네임입니다." }
            }
            user.nickname = it
        }
        req.birthdate?.let { user.birthdate = it }
    }

    @Transactional
    fun deleteAccount(email: String) {
        val user = userRepository.findByEmail(email).orElseThrow()
        refreshTokenRepo.deleteByUserId(user.id)
        emailTokenRepo.deleteAllByUserId(user.id)
        userRepository.delete(user)
    }
}