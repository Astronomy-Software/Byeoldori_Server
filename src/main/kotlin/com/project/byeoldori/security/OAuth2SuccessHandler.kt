package com.project.byeoldori.security

import com.project.byeoldori.user.entity.RefreshToken
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.RefreshTokenRepository
import com.project.byeoldori.user.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.io.IOException

@Component
class OAuth2SuccessHandler(
    private val jwtUtil: JwtUtil,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) : AuthenticationSuccessHandler {

    @Throws(IOException::class)
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oauthUser = authentication.principal as org.springframework.security.oauth2.core.user.OAuth2User
        val email = oauthUser.attributes["email"] as String

        var user = userRepository.findByEmail(email).orElse(null)
        if (user == null) {
            user = User(
                email = email,
                password = "OAUTH_USER",  // 명확한 마킹
                nickname = null, // 프론트에서 추후 수정 가능
                birthdate = null,
                emailVerified = true,
                termsOfService = true,
                privacyPolicy = true,
                marketing = false,
                location = false
            )
            userRepository.save(user)
        }

        val accessToken = jwtUtil.generateAccessToken(email)
        val refreshToken = jwtUtil.generateRefreshToken(email)

        refreshTokenRepository.save(
            RefreshToken(email, refreshToken, jwtUtil.extractExpiration(refreshToken))
        )

        response.setHeader("Authorization", "Bearer $accessToken")
        response.setHeader("Refresh-Token", refreshToken)
        response.status = HttpServletResponse.SC_OK
    }
}