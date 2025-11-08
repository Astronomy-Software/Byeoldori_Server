package com.project.byeoldori.user.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.security.CurrentUserResolver
import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.entity.EmailVerificationToken
import com.project.byeoldori.user.entity.RefreshToken
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.*
import com.project.byeoldori.user.utils.PasswordValidator
import com.project.byeoldori.user.utils.PhoneNormalizer
import com.project.byeoldori.user.utils.TemporaryPassword
import com.project.byeoldori.user.utils.TokenHasher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

@Service
class UserService(
    @Value("\${storage.public-base-url}") private val publicBaseUrl: String,
    private val currentUser: CurrentUserResolver,
    private val storage: StorageService,
    private val userRepository: UserRepository,
    private val emailTokenRepo: EmailVerificationTokenRepository,
    private val refreshTokenRepo: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwt: JwtUtil,
    private val emailService: EmailService,
    private val currentUserResolver: CurrentUserResolver,
    private val googleVerifier: GoogleIdTokenVerifier
) {

    private val log = LoggerFactory.getLogger(UserService::class.java)

    @Transactional
    fun signup(req: SignupRequestDto) {
        if (req.password != req.passwordConfirm) throw InvalidInputException(ErrorCode.PASSWORD_MISMATCH.message)
        if (!PasswordValidator.isValid(req.password)) throw InvalidInputException(ErrorCode.INVALID_PASSWORD_FORMAT.message)
        if (userRepository.existsByEmail(req.email)) throw ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS)
        if (req.nickname?.isNotBlank() == true) {
            if (userRepository.existsByNickname(req.nickname)) throw ConflictException(ErrorCode.NICKNAME_ALREADY_EXISTS)
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
            .orElseThrow { InvalidInputException(ErrorCode.INVALID_TOKEN.message) }
        if (!token.isUsable()) throw InvalidInputException("토큰이 만료되었습니다.")

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
            .orElseThrow { NotFoundException(ErrorCode.USER_NOT_FOUND, "이메일 또는 비밀번호가 올바르지 않습니다.") }

        if (user.provider != null) {
            throw ConflictException(ErrorCode.LOGIN_METHOD_MISMATCH, "${user.provider} 계정입니다. ${user.provider}으로 로그인해주세요.")
        }

        if (!user.emailVerified) throw ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED.message)
        if (!passwordEncoder.matches(req.password, user.passwordHash)) {
            throw InvalidInputException("이메일 또는 비밀번호가 올바르지 않습니다.")
        }

        return issueTokensAndGetResponse(user)
    }

    @Transactional
    fun reissue(req: TokenReissueRequestDto): AuthResponseDto {
        if (!jwt.validateToken(req.refreshToken) || !jwt.isTokenType(req.refreshToken, "refresh")) {
            throw UnauthorizedException(ErrorCode.INVALID_TOKEN.message)
        }
        val email = jwt.extractEmail(req.refreshToken)
        val user = userRepository.findByEmail(email)
            .orElseThrow { NotFoundException(ErrorCode.USER_NOT_FOUND) }

        val stored = refreshTokenRepo.findByUserIdForUpdate(user.id)
            .orElseThrow { NotFoundException(ErrorCode.REFRESH_TOKEN_NOT_FOUND) }

        if (stored.tokenHash != TokenHasher.sha256Hex(req.refreshToken)) {
            throw UnauthorizedException("리프레시 토큰이 일치하지 않습니다.")
        }

        if (!stored.isActive()) throw UnauthorizedException("리프레시 토큰이 만료되었거나 폐기되었습니다.")

        val newAccess = jwt.generateAccessToken(email)
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
    fun logout() {
        val user = currentUserResolver.getUser()
        refreshTokenRepo.deleteByUserId(user.id)
    }

    @Transactional
    fun resetPasswordByIdentity(req: PasswordResetRequestDto) {
        val user = userRepository.findByEmail(req.email)
            .orElseThrow { NotFoundException(ErrorCode.ACCOUNT_INFO_MISMATCH) }

        val nameMatches = (user.name).trim().equals(req.name.trim(), ignoreCase = true)
        val phoneMatches = PhoneNormalizer.normalize(user.phone) == PhoneNormalizer.normalize(req.phone)
        if (!nameMatches || !phoneMatches) {
            throw NotFoundException(ErrorCode.ACCOUNT_INFO_MISMATCH)
        }

        val tempPw = TemporaryPassword.generate(12)
        user.passwordHash = passwordEncoder.encode(tempPw)

        refreshTokenRepo.deleteByUserId(user.id)

        emailService.sendTemporaryPassword(
            to = user.email,
            name = user.name,
            tempPassword = tempPw
        )
    }

    @Transactional
    fun changePassword(req: ChangePasswordRequest) {
        val user = currentUserResolver.getUser()

        if (!passwordEncoder.matches(req.currentPassword, user.passwordHash)) {
            throw InvalidInputException(ErrorCode.CURRENT_PASSWORD_MISMATCH.message)
        }
        if (req.newPassword != req.confirmNewPassword) {
            throw InvalidInputException(ErrorCode.PASSWORD_MISMATCH.message)
        }
        if (passwordEncoder.matches(req.newPassword, user.passwordHash)) {
            throw InvalidInputException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD.message)
        }

        user.passwordHash = passwordEncoder.encode(req.newPassword)
        refreshTokenRepo.deleteByUserId(user.id)
    }

    @Transactional(readOnly = true)
    fun getMe(): UserMeResponseDto {
        val user = currentUserResolver.getUser()
        return UserMeResponseDto.from(user)
    }

    @Transactional
    fun updateMe(req: UserUpdateRequestDto) {
        val user = currentUserResolver.getUser()
        req.nickname?.let {
            if (it.isNotBlank() && it != user.nickname) {
                if (userRepository.existsByNickname(it)) throw ConflictException(ErrorCode.NICKNAME_ALREADY_EXISTS)
            }
            user.nickname = it
        }
        req.birthdate?.let { user.birthdate = it }
        req.phone?.let { user.phone = it }
    }

    @Transactional
    fun updateProfileImage(image: MultipartFile): String {
        if (image.isEmpty) throw InvalidInputException("이미지 파일이 비어있습니다.")

        val user = currentUser.getUser()
        val oldUrl = user.profileImageUrl

        // 저장 (형식/용량/픽셀 검증은 StorageService 구현이 처리)
        val newUrl = storage.storeImage(image)
        user.profileImageUrl = newUrl

        if (!oldUrl.isNullOrBlank() && oldUrl.startsWith(publicBaseUrl.trimEnd('/'))) {
            try {
                storage.deleteImageByUrl(oldUrl)
            } catch (e: Exception) {
                log.warn("이전 프로필 이미지 삭제 실패: {}", e.message)
            }
        }
        return newUrl
    }

    @Transactional
    fun deleteAccount() {
        val user = currentUserResolver.getUser()
        refreshTokenRepo.deleteByUserId(user.id)
        emailTokenRepo.deleteAllByUserId(user.id)
        userRepository.delete(user)
    }

    @Transactional
    fun loginWithGoogleIdToken(idToken: String): AuthResponseDto {
        if (idToken.isBlank()) {
            throw InvalidInputException("idToken이 필요합니다.")
        }
        val verified = googleVerifier.verify(idToken)
            ?: throw UnauthorizedException(ErrorCode.GOOGLE_ID_TOKEN_INVALID.message)
        val payload = verified.payload

        val sub = payload.subject // Google 고유 사용자 ID
        val email = (payload.email ?: "").lowercase()
        val emailVerifiedByGoogle: Boolean = when (val v = payload["email_verified"]) {
            is Boolean -> v
            is String -> v.equals("true", ignoreCase = true)
            else -> payload.emailVerified == true
        }
        val nameFromGoogle = payload["name"]?.toString()
        val pictureFromGoogle = payload["picture"]?.toString()

        var user = userRepository.findByProviderAndProviderId("google", sub)

        if (user == null && email.isNotBlank()) {
            val byEmail = userRepository.findByEmail(email).orElse(null)
            if (byEmail != null) {
                // 구글 이메일이 검증된 경우에만 자동 병합
                if (!emailVerifiedByGoogle) {
                    // 추가 인증 필요: 자동 병합 금지
                    throw ConflictException(
                        ErrorCode.ACCOUNT_ALREADY_EXISTS_WITH_DIFFERENT_PROVIDER,
                        "이미 가입된 이메일입니다. 이메일 인증을 완료하거나, 계정 설정에서 구글 계정을 연동해주세요."
                    )
                }
                user = byEmail
            }
        }

        if (user == null) {
            user = User(
                email = email,
                passwordHash = "OAUTH2:google:$sub",
                name = nameFromGoogle ?: "User",
                phone = "",
                nickname = nameFromGoogle ?: "User"
            )
        }

        user.provider = "google"
        user.providerId = sub
        if (!pictureFromGoogle.isNullOrBlank() && user.profileImageUrl.isNullOrBlank()) {
            user.profileImageUrl = pictureFromGoogle
        }
        if (emailVerifiedByGoogle && email.isNotBlank()) {
            user.emailVerified = true      // 비어있을 때만 채움
        }

        userRepository.save(user)

        return issueTokensAndGetResponse(user)
    }

    private fun issueTokensAndGetResponse(user: User): AuthResponseDto {
        val access = jwt.generateAccessToken(user.email)
        val refresh = jwt.generateRefreshToken(user.email)
        val hash = TokenHasher.sha256Hex(refresh)
        val exp = jwt.extractExpiration(refresh)

        val existing = refreshTokenRepo.findByUserIdForUpdate(user.id)
        if (existing.isPresent) {
            val t = existing.get()
            t.tokenHash = hash
            t.expiresAt = exp
            t.revokedAt = null
            t.rotatedAt = null
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
}