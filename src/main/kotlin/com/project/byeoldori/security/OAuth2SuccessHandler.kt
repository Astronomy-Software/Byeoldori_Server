package com.project.byeoldori.security

import com.project.byeoldori.user.entity.RefreshToken
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.RefreshTokenRepository
import com.project.byeoldori.user.repository.UserRepository
import com.project.byeoldori.user.utils.TokenHasher
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.io.IOException
import java.time.LocalDateTime
import java.util.*

@Component
class OAuth2SuccessHandler(
    private val jwtUtil: JwtUtil,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder
) : AuthenticationSuccessHandler {

    @Throws(IOException::class)
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oauthUser = authentication.principal as OAuth2User

        val email: String = oauthUser.getAttribute<String>("email")
            ?: run {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "OAuth2 provider did not return email")
                return
            }

        // 사용자 조회/생성
        var user: User? = userRepository.findByEmail(email).orElse(null)
        if (user == null) {
            val name = oauthUser.getAttribute<String>("name")?.take(12) ?: "OAuth User"
            val randomPwd = "OAUTH::" + UUID.randomUUID()
            user = userRepository.save(
                User(
                    email = email,
                    passwordHash = passwordEncoder.encode(randomPwd),
                    name = name,
                    phone = "000-0000-0000",
                    nickname = null,
                    birthdate = null,
                    emailVerified = true,  // 소셜 로그인은 이메일 검증 완료로 간주
                    roles = mutableSetOf("USER")
                )
            )
        }

        val accessToken = jwtUtil.generateAccessToken(email)
        val refreshToken = jwtUtil.generateRefreshToken(email)
        val hash = TokenHasher.sha256Hex(refreshToken)
        val exp  = jwtUtil.extractExpiration(refreshToken)

        val existing = refreshTokenRepository.findByUserIdForUpdate(user!!.id) // PESSIMISTIC_WRITE 권장
            .orElse(null)

        if (existing != null) {
            existing.tokenHash = hash
            existing.expiresAt = exp
            existing.revokedAt = null
            existing.rotatedAt = null

        } else {
            refreshTokenRepository.save(
                RefreshToken(
                    user = user,
                    tokenHash = hash,
                    expiresAt = exp
                )
            )
        }

        user.lastLoginAt = LocalDateTime.now()

        response.setHeader("Authorization", "Bearer $accessToken")
        response.setHeader("Refresh-Token", refreshToken)
        response.status = HttpServletResponse.SC_OK
    }
}