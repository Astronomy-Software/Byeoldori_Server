package com.project.byeoldori.user.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.security.CurrentUserResolver
import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.entity.EmailVerificationToken
import com.project.byeoldori.user.entity.PasswordResetToken
import com.project.byeoldori.user.entity.RefreshToken
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.*
import com.project.byeoldori.user.utils.PasswordValidator
import com.project.byeoldori.user.utils.PhoneNormalizer
import com.project.byeoldori.user.utils.TokenHasher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime

@Service
class UserService(
    @Value("\${storage.public-base-url}") private val publicBaseUrl: String,
    @Value("\${google.client-id}") private val googleClientId: String,
    @Value("\${google.client-secret}") private val googleClientSecret: String,
    @Value("\${kakao.client-id}") private val kakaoClientId: String,
    @Value("\${kakao.client-secret}") private val kakaoClientSecret: String,
    @Value("\${naver.client-id}") private val naverClientId: String,
    @Value("\${naver.client-secret}") private val naverClientSecret: String,
    private val currentUserResolver: CurrentUserResolver,
    private val storage: StorageService,
    private val userRepository: UserRepository,
    private val emailTokenRepo: EmailVerificationTokenRepository,
    private val refreshTokenRepo: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwt: JwtUtil,
    private val emailService: EmailService,
    private val cachedUserLookupService: CachedUserLookupService,
    private val passwordResetTokenRepo: PasswordResetTokenRepository
) {

    private val log = LoggerFactory.getLogger(UserService::class.java)
    private val zone = java.time.ZoneId.systemDefault()

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
    fun reissue(refreshToken: String): AuthResponseDto {
        if (!jwt.validateToken(refreshToken) || !jwt.isTokenType(refreshToken, "refresh")) {
            throw UnauthorizedException(ErrorCode.INVALID_TOKEN.message)
        }
        val email = jwt.extractEmail(refreshToken)
        val user = userRepository.findByEmail(email)
            .orElseThrow { NotFoundException(ErrorCode.USER_NOT_FOUND) }

        val stored = refreshTokenRepo.findByUserIdForUpdate(user.id)
            .orElseThrow { NotFoundException(ErrorCode.REFRESH_TOKEN_NOT_FOUND) }

        if (stored.tokenHash != TokenHasher.sha256Hex(refreshToken)) {
            throw UnauthorizedException("리프레시 토큰이 일치하지 않습니다.")
        }

        if (!stored.isActive()) throw UnauthorizedException("리프레시 토큰이 만료되었거나 폐기되었습니다.")

        val newAccess = jwt.generateAccessToken(email)
        val newRefresh = jwt.generateRefreshToken(email)

        stored.tokenHash = TokenHasher.sha256Hex(newRefresh)
        stored.expiresAt = jwt.extractExpiration(newRefresh)
        stored.rotatedAt = LocalDateTime.now()

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
        cachedUserLookupService.evictByEmail(user.email)
    }

    @Transactional
    fun resetPasswordByIdentity(req: PasswordResetRequestDto) {
        val user = userRepository.findByEmail(req.email)
            .orElseThrow { NotFoundException(ErrorCode.ACCOUNT_INFO_MISMATCH) }

        val nameMatches = user.name.trim().equals(req.name.trim(), ignoreCase = true)
        val phoneMatches = PhoneNormalizer.normalize(user.phone) == PhoneNormalizer.normalize(req.phone)
        if (!nameMatches || !phoneMatches) {
            throw NotFoundException(ErrorCode.ACCOUNT_INFO_MISMATCH)
        }

        passwordResetTokenRepo.deleteAllByUserId(user.id)
        val resetToken = passwordResetTokenRepo.save(PasswordResetToken(user = user))

        emailService.sendPasswordResetLink(
            to = user.email,
            name = user.name,
            token = resetToken.id
        )
    }

    @Transactional
    fun confirmPasswordReset(req: PasswordResetConfirmDto) {
        if (req.newPassword != req.confirmNewPassword) {
            throw InvalidInputException(ErrorCode.PASSWORD_MISMATCH.message)
        }
        if (!PasswordValidator.isValid(req.newPassword)) {
            throw InvalidInputException(ErrorCode.INVALID_PASSWORD_FORMAT.message)
        }

        val token = passwordResetTokenRepo.findByIdAndUsedAtIsNull(req.token)
            .orElseThrow { InvalidInputException(ErrorCode.INVALID_TOKEN.message) }
        if (!token.isUsable()) throw InvalidInputException("재설정 링크가 만료되었습니다.")

        token.user.passwordHash = passwordEncoder.encode(req.newPassword)
        token.usedAt = java.time.LocalDateTime.now()

        refreshTokenRepo.deleteByUserId(token.user.id)
        cachedUserLookupService.evictByEmail(token.user.email)
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
        cachedUserLookupService.evictByEmail(user.email)
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
        cachedUserLookupService.evictByEmail(user.email)
    }

    @Transactional
    fun updateProfileImage(image: MultipartFile): String {
        if (image.isEmpty) throw InvalidInputException("이미지 파일이 비어있습니다.")

        val user = currentUserResolver.getUser()
        val oldUrl = user.profileImageUrl

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
        val originalEmail = user.email

        refreshTokenRepo.deleteByUserId(user.id)
        emailTokenRepo.deleteAllByUserId(user.id)
        passwordResetTokenRepo.deleteAllByUserId(user.id)

        // 소프트 딜리트: PII 익명화 후 삭제 시각 기록 (게시글/댓글은 "탈퇴한 사용자"로 유지)
        user.passwordHash = "DELETED"
        user.phone = ""
        user.nickname = null
        user.birthdate = null
        user.profileImageUrl = null
        user.provider = null
        user.providerId = null
        user.emailVerified = false
        user.deletedAt = LocalDateTime.now()
        userRepository.save(user)

        cachedUserLookupService.evictByEmail(originalEmail)
    }

    // ─────────────────────────────────────────────────────
    // 소셜 로그인 (Authorization Code Flow)
    // ─────────────────────────────────────────────────────

    @Transactional
    fun loginWithGoogle(code: String, redirectUri: String): AuthResponseDto {
        val tokenResponse = exchangeCodeForToken(
            tokenUrl = "https://oauth2.googleapis.com/token",
            clientId = googleClientId,
            clientSecret = googleClientSecret,
            redirectUri = redirectUri,
            code = code
        )
        val accessToken = tokenResponse["access_token"] as? String
            ?: throw UnauthorizedException("Google 토큰 교환 실패")

        val userInfo = fetchUserInfo(
            userInfoUrl = "https://www.googleapis.com/oauth2/v2/userinfo",
            accessToken = accessToken
        )
        val providerId = userInfo["id"]?.toString() ?: throw UnauthorizedException("Google 사용자 정보 조회 실패")
        val email = userInfo["email"]?.toString()?.lowercase() ?: ""
        val name = userInfo["name"]?.toString()
        val picture = userInfo["picture"]?.toString()
        // Google userinfo v2 는 이메일 소유 검증 여부를 verified_email 로 제공
        val emailVerified = toBoolOrNull(userInfo["verified_email"] ?: userInfo["email_verified"])

        val user = findOrCreateOAuthUser("google", providerId, email, name, picture, emailVerified)
        return issueTokensAndGetResponse(user)
    }

    @Transactional
    fun loginWithKakao(code: String, redirectUri: String): AuthResponseDto {
        val tokenResponse = exchangeCodeForToken(
            tokenUrl = "https://kauth.kakao.com/oauth/token",
            clientId = kakaoClientId,
            clientSecret = kakaoClientSecret,
            redirectUri = redirectUri,
            code = code
        )
        val accessToken = tokenResponse["access_token"] as? String
            ?: throw UnauthorizedException("Kakao 토큰 교환 실패")

        val userInfo = fetchUserInfo(
            userInfoUrl = "https://kapi.kakao.com/v2/user/me",
            accessToken = accessToken
        )
        val providerId = userInfo["id"]?.toString() ?: throw UnauthorizedException("Kakao 사용자 정보 조회 실패")

        @Suppress("UNCHECKED_CAST")
        val kakaoAccount = userInfo["kakao_account"] as? Map<String, Any> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val properties = userInfo["properties"] as? Map<String, Any> ?: emptyMap()

        val email = kakaoAccount["email"]?.toString()?.lowercase() ?: ""
        val nickname = properties["nickname"]?.toString()
        // Kakao 는 kakao_account.is_email_verified 로 이메일 소유 검증 여부 제공
        val emailVerified = toBoolOrNull(kakaoAccount["is_email_verified"])

        val user = findOrCreateOAuthUser("kakao", providerId, email, nickname, null, emailVerified)
        return issueTokensAndGetResponse(user)
    }

    @Transactional
    fun loginWithNaver(code: String, redirectUri: String): AuthResponseDto {
        val tokenResponse = exchangeCodeForToken(
            tokenUrl = "https://nid.naver.com/oauth2.0/token",
            clientId = naverClientId,
            clientSecret = naverClientSecret,
            redirectUri = redirectUri,
            code = code
        )
        val accessToken = tokenResponse["access_token"] as? String
            ?: throw UnauthorizedException("Naver 토큰 교환 실패")

        val userInfoResponse = fetchUserInfo(
            userInfoUrl = "https://openapi.naver.com/v1/nid/me",
            accessToken = accessToken
        )

        @Suppress("UNCHECKED_CAST")
        val naverUser = userInfoResponse["response"] as? Map<String, Any>
            ?: throw UnauthorizedException("Naver 사용자 정보 조회 실패")

        val providerId = naverUser["id"]?.toString() ?: throw UnauthorizedException("Naver ID 없음")
        val email = naverUser["email"]?.toString()?.lowercase() ?: ""
        val nickname = naverUser["nickname"]?.toString()
        val picture = naverUser["profile_image"]?.toString()
        // Naver 는 이메일 소유 검증 플래그를 제공하지 않음 → null (로컬 계정 자동 연동 불가)
        val user = findOrCreateOAuthUser("naver", providerId, email, nickname, picture, null)
        return issueTokensAndGetResponse(user)
    }

    // ─────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────

    private fun exchangeCodeForToken(
        tokenUrl: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        code: String
    ): Map<*, *> {
        val formData = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            add("client_secret", clientSecret)
            add("redirect_uri", redirectUri)
            add("code", code)
        }

        return WebClient.create()
            .post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .onStatus({ it.isError }) { res ->
                res.bodyToMono(String::class.java).map {
                    UnauthorizedException("OAuth 토큰 교환 실패: $it")
                }
            }
            .bodyToMono(Map::class.java)
            .block() ?: throw UnauthorizedException("OAuth 토큰 교환 응답 없음")
    }

    private fun fetchUserInfo(userInfoUrl: String, accessToken: String): Map<*, *> {
        return WebClient.create()
            .get()
            .uri(userInfoUrl)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .onStatus({ it.isError }) { res ->
                res.bodyToMono(String::class.java).map {
                    UnauthorizedException("OAuth 사용자 정보 조회 실패: $it")
                }
            }
            .bodyToMono(Map::class.java)
            .block() ?: throw UnauthorizedException("OAuth 사용자 정보 응답 없음")
    }

    private fun findOrCreateOAuthUser(
        provider: String,
        providerId: String,
        email: String,
        name: String?,
        picture: String?,
        providerEmailVerified: Boolean?
    ): User {
        var user = userRepository.findByProviderAndProviderId(provider, providerId)

        if (user == null && email.isNotBlank()) {
            val byEmail = userRepository.findByEmail(email).orElse(null)
            if (byEmail != null) {
                when {
                    // 다른 소셜 provider 로 이미 가입된 이메일 → 연동 불가 (기존 동작 유지)
                    byEmail.provider != null && byEmail.provider != provider -> {
                        throw ConflictException(
                            ErrorCode.ACCOUNT_ALREADY_EXISTS_WITH_DIFFERENT_PROVIDER,
                            "이미 ${byEmail.provider} 계정으로 가입된 이메일입니다."
                        )
                    }
                    // 로컬(비밀번호) 계정에 소셜을 email 만으로 자동 연동하는 경로는 계정 탈취 벡터.
                    // provider 가 email_verified=true 로 이메일 소유를 확인해줄 때만 연동 허용.
                    // (플래그 미제공(null)/false 면 자동 연동 금지 → 명확한 예외)
                    byEmail.provider == null && providerEmailVerified != true -> {
                        throw ConflictException(
                            ErrorCode.ACCOUNT_ALREADY_EXISTS_WITH_DIFFERENT_PROVIDER,
                            "이미 해당 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인해주세요."
                        )
                    }
                    // 로컬 계정(검증됨) 또는 동일 provider 계정 → 연동
                    else -> user = byEmail
                }
            }
        }

        if (user == null) {
            val effectiveEmail = if (email.isBlank()) "$provider.$providerId@noemail.byeoldori" else email
            user = User(
                email = effectiveEmail,
                passwordHash = "OAUTH2:$provider:$providerId",
                name = name ?: "User",
                phone = "",
                nickname = null
            )
        }

        user.provider = provider
        user.providerId = providerId
        user.emailVerified = email.isNotBlank()
        if (!picture.isNullOrBlank() && user.profileImageUrl.isNullOrBlank()) {
            user.profileImageUrl = picture
        }

        return userRepository.save(user)
    }

    // OAuth 응답의 email_verified 류 값을 안전하게 Boolean? 으로 해석 (Boolean / "true"/"false" 문자열 모두 대응)
    private fun toBoolOrNull(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
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
        return AuthResponseDto.of(
            access, refresh,
            jwt.extractExpiration(access).atZone(zone).toInstant(),
            jwt.extractExpiration(refresh).atZone(zone).toInstant()
        )
    }
}
