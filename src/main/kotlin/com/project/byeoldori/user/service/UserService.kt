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
import com.project.byeoldori.user.utils.DeletedAccount
import com.project.byeoldori.user.utils.EmailMasker
import com.project.byeoldori.user.utils.NicknameRules
import com.project.byeoldori.user.utils.PasswordValidator
import com.project.byeoldori.user.utils.PhoneNormalizer
import com.project.byeoldori.user.utils.TokenHasher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
        // 내부 전용 도메인(탈퇴 계정 자리표시자·이메일 없는 소셜 계정)은 가입 이메일로 쓸 수 없다
        val emailDomain = req.email.substringAfterLast('@').lowercase()
        if (emailDomain == DeletedAccount.EMAIL_DOMAIN || emailDomain == NO_EMAIL_DOMAIN) {
            throw InvalidInputException("사용할 수 없는 이메일입니다.")
        }
        val existing = userRepository.findByEmail(req.email).orElse(null)
        if (existing != null && !isAbandonedSignup(existing)) throw ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS)
        val nickname = NicknameRules.normalize(req.nickname)
        if (nickname != null && nickname != existing?.nickname) validateNewNickname(nickname)

        val user = if (existing != null) {
            // 인증 링크가 모두 만료된 채 버려진 가입 → 새 가입 정보로 덮어쓰고 인증 메일을 다시 보낸다.
            // (그대로 두면 이메일 주인이 영영 가입하지 못하고, 남의 이메일을 미인증 가입으로 영구 선점할 수 있다)
            emailTokenRepo.deleteAllByUserId(existing.id)
            existing.passwordHash = passwordEncoder.encode(req.password)
            existing.name = req.name
            existing.phone = req.phone
            existing.nickname = nickname
            existing.birthdate = req.birthdate
            userRepository.save(existing)
        } else {
            userRepository.save(
                User(
                    email = req.email,
                    passwordHash = passwordEncoder.encode(req.password),
                    name = req.name,
                    phone = req.phone,
                    nickname = nickname,
                    birthdate = req.birthdate
                )
            )
        }

        val token = emailTokenRepo.save(EmailVerificationToken(user = user))
        emailService.sendEmailVerification(user.email, token.id)
    }

    // 이메일 인증을 끝내지 못했고 쓸 수 있는 인증 링크도 남지 않은 로컬 가입인지.
    // 한 번도 로그인하지 않은(=글·댓글 등이 없을) 계정만 해당한다. 링크가 살아 있는 동안(24시간)은 덮어쓰지 않는다.
    private fun isAbandonedSignup(user: User): Boolean =
        user.provider == null &&
            !user.emailVerified &&
            user.deletedAt == null &&
            user.lastLoginAt == null &&
            !emailTokenRepo.existsUsableByUserId(user.id, LocalDateTime.now())

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
        // 인증 없이 이름+전화번호만으로 호출되므로 이메일 원문 대신 마스킹한 값을 돌려준다
        return userRepository.findAllByNameAndPhone(name, phone)
            .filter { it.deletedAt == null }
            .map { EmailMasker.mask(it.email) }
    }

    @Transactional
    fun login(req: LoginRequestDto): AuthResponseDto {
        // 미가입·탈퇴·비밀번호 오류는 같은 상태코드(400)와 메시지로 응답해 계정 존재 여부를 드러내지 않는다
        val user = userRepository.findByEmail(req.email).orElse(null)
            ?.takeIf { it.deletedAt == null }
            ?: throw InvalidInputException(LOGIN_FAILED_MESSAGE)

        if (user.provider != null) {
            throw ConflictException(ErrorCode.LOGIN_METHOD_MISMATCH, "${user.provider} 계정입니다. ${user.provider}으로 로그인해주세요.")
        }

        // 비밀번호를 먼저 확인해야 비밀번호를 모르는 사람에게 "인증 안 된 계정"이라는 정보가 새지 않는다
        if (!passwordEncoder.matches(req.password, user.passwordHash)) {
            throw InvalidInputException(LOGIN_FAILED_MESSAGE)
        }
        if (!user.emailVerified) throw ForbiddenException(ErrorCode.EMAIL_NOT_VERIFIED.message)

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
        if (user.deletedAt != null) throw UnauthorizedException("탈퇴한 계정입니다.")

        // 기기(세션)마다 행이 따로 있으므로 제시된 토큰의 해시로 그 행만 찾는다.
        // 이미 회전된 옛 토큰·로그아웃된 토큰은 행이 없어 거부된다.
        val stored = refreshTokenRepo.findByTokenHashForUpdate(TokenHasher.sha256Hex(refreshToken))
            .orElse(null)
            ?.takeIf { it.user.id == user.id }
            ?: throw UnauthorizedException("리프레시 토큰이 일치하지 않습니다.")

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

    // refreshToken 이 있으면 그 기기의 세션만 끝내고, 없으면(토큰을 보내지 않는 클라이언트) 모든 세션을 끝낸다
    @Transactional
    fun logout(refreshToken: String? = null) {
        val user = currentUserResolver.getUser()
        if (refreshToken.isNullOrBlank()) {
            refreshTokenRepo.deleteByUserId(user.id)
        } else {
            refreshTokenRepo.deleteByUserIdAndTokenHash(user.id, TokenHasher.sha256Hex(refreshToken))
        }
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
        req.nickname?.let { raw ->
            // 빈 닉네임("")을 그대로 저장하면 UNIQUE 충돌(두 번째 사용자부터 500)이 나므로 거부한다
            val nickname = NicknameRules.normalize(raw) ?: throw InvalidInputException("닉네임을 입력해주세요.")
            if (nickname != user.nickname) {
                validateNewNickname(nickname)
                user.nickname = nickname
            }
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
        val profileImageUrl = user.profileImageUrl

        refreshTokenRepo.deleteByUserId(user.id)
        emailTokenRepo.deleteAllByUserId(user.id)
        passwordResetTokenRepo.deleteAllByUserId(user.id)

        // 소프트 딜리트: PII 익명화 후 삭제 시각 기록 (게시글/댓글은 "탈퇴한 사용자"로 유지)
        // email 은 UNIQUE 라 그대로 두면 같은 이메일로 재가입·소셜 로그인을 영영 못 한다 → 자리표시자로 바꾼다
        user.email = DeletedAccount.tombstoneEmail(user.id)
        user.name = DeletedAccount.ANONYMOUS_NAME
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
        deleteOwnProfileImageAfterCommit(profileImageUrl)
    }

    // 탈퇴한 사용자의 프로필 사진(얼굴일 수 있는 PII)이 /files 공개 URL 로 계속 열리지 않게 실제 파일도 지운다.
    // 우리 저장소 파일(publicBaseUrl)만 대상(소셜 프로필 사진 같은 외부 URL 은 건드리지 않음),
    // 롤백되면 파일만 사라지지 않도록 커밋이 성공한 뒤에 지운다.
    private fun deleteOwnProfileImageAfterCommit(url: String?) {
        if (url.isNullOrBlank() || !url.startsWith(publicBaseUrl.trimEnd('/'))) return
        val delete = {
            try {
                storage.deleteImageByUrl(url)
            } catch (e: Exception) {
                log.warn("탈퇴 회원 프로필 이미지 삭제 실패: {}", e.message)
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = delete()
            })
        } else {
            delete()
        }
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
        // 탈퇴(deletedAt) 계정에는 다시 연결하지 않는다(연결돼도 인증 필터가 거부해 쓸 수 없는 토큰만 발급된다)
        var user = userRepository.findByProviderAndProviderId(provider, providerId)?.takeIf { it.deletedAt == null }

        if (user == null && email.isNotBlank()) {
            val byEmail = userRepository.findByEmail(email).orElse(null)?.takeIf { it.deletedAt == null }
            if (byEmail != null) {
                when {
                    // 다른 소셜 provider 로 이미 가입된 이메일 → 연동 불가 (기존 동작 유지)
                    byEmail.provider != null && byEmail.provider != provider -> {
                        throw ConflictException(
                            ErrorCode.ACCOUNT_ALREADY_EXISTS_WITH_DIFFERENT_PROVIDER,
                            "이미 ${byEmail.provider} 계정으로 가입된 이메일입니다."
                        )
                    }
                    // 같은 provider 의 "다른" 계정(providerId 다름)이 이메일만 같은 경우 → 연동 불가.
                    // 같은 provider 계정은 providerId 로만 식별한다(이메일만 맞춰 남의 계정에 들어가는 것 방지).
                    byEmail.provider == provider && byEmail.providerId != providerId -> {
                        throw ConflictException(
                            ErrorCode.ACCOUNT_ALREADY_EXISTS_WITH_DIFFERENT_PROVIDER,
                            "이미 다른 $provider 계정으로 가입된 이메일입니다."
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
                    // 로컬 계정(검증됨) → 연동
                    else -> user = byEmail
                }
            }
        }

        if (user == null) {
            val effectiveEmail = if (email.isBlank()) "$provider.$providerId@$NO_EMAIL_DOMAIN" else email
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

    // 새로 쓰려는 닉네임(정규화된 값)의 길이·중복 검사
    private fun validateNewNickname(nickname: String) {
        if (nickname.length > NicknameRules.MAX_LENGTH) {
            throw InvalidInputException("닉네임은 ${NicknameRules.MAX_LENGTH}자 이하로 입력해주세요.")
        }
        if (userRepository.existsByNickname(nickname)) throw ConflictException(ErrorCode.NICKNAME_ALREADY_EXISTS)
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

        // 로그인마다 새 세션 행을 만든다(다른 기기의 세션은 그대로 유지).
        // 만료·폐기된 행은 지우고, 세션이 상한을 넘으면 가장 오래된 것부터 끝낸다.
        refreshTokenRepo.deleteInactiveByUserId(user.id, LocalDateTime.now())
        val sessions = refreshTokenRepo.findAllByUserIdNewestFirst(user.id)
        if (sessions.size >= MAX_SESSIONS_PER_USER) {
            refreshTokenRepo.deleteAll(sessions.drop(MAX_SESSIONS_PER_USER - 1))
        }
        refreshTokenRepo.save(RefreshToken(user = user, tokenHash = hash, expiresAt = exp))

        user.lastLoginAt = LocalDateTime.now()
        return AuthResponseDto.of(
            access, refresh,
            jwt.extractExpiration(access).atZone(zone).toInstant(),
            jwt.extractExpiration(refresh).atZone(zone).toInstant()
        )
    }

    private companion object {
        const val LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다."
        // 이메일을 주지 않는 소셜 계정의 자리표시자 도메인
        const val NO_EMAIL_DOMAIN = "noemail.byeoldori"
        // 사용자당 동시에 유지하는 로그인 세션(refresh 토큰) 수 상한
        const val MAX_SESSIONS_PER_USER = 10
    }
}
