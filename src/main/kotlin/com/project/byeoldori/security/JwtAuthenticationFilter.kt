package com.project.byeoldori.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.repository.UserRepository
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import io.jsonwebtoken.security.SignatureException
import java.nio.charset.StandardCharsets

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,  // JWT 생성/검증 유틸리티 주입
    private val userRepo: UserRepository,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    // 요청 헤더에서 JWT를 추출하고, 유효하면 SecurityContext에 인증 정보를 등록
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 요청 헤더에서 Authorization 값을 가져옴.
        val authHeader = request.getHeader("Authorization")

        // 헤더에서 토큰을 추출 "Bearer eyJ..." 형식에서 토큰만 잘라냄
        val token = extractToken(authHeader)

        // 토큰이 존재할 경우에만 검증 시도
        if (token != null) {
            try {
                // 1. 유효성 검증을 시도하고, 만료 등 예외 발생 시 catch
                if (jwtUtil.validateToken(token)) {
                    val userEmail = jwtUtil.extractEmail(token)
                    userRepo.findByEmail(userEmail).orElse(null)?.let { user ->
                        val authentication = UsernamePasswordAuthenticationToken(
                            user, null, emptyList()
                        )
                        authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                        SecurityContextHolder.getContext().authentication = authentication
                        request.setAttribute("currentUser", user)
                    }
                }
            } catch (e: ExpiredJwtException) {
                logger.warn("Access Token 만료 - IP: ${request.remoteAddr}")
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Access Token이 만료되었습니다.")
                return
            } catch (e: SignatureException) {
                logger.warn("JWT 서명 오류 발생 - IP: ${request.remoteAddr}")
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "잘못된 서명입니다.")
                return
            } catch (e: MalformedJwtException) {
                logger.warn("JWT 형식 오류 발생 - IP: ${request.remoteAddr}")
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "잘못된 토큰 형식입니다.")
                return
            } catch (e: Exception) {
                logger.error("알 수 없는 JWT 인증 오류 - IP: ${request.remoteAddr}", e)
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "인증에 실패했습니다.")
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    // 토큰 추출
    private fun extractToken(header: String?): String? {
        return if (header != null && header.startsWith("Bearer ")) {
            header.substring(7)
        } else null
    }

    private fun sendErrorResponse(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(response.writer, ApiResponse.fail<Unit>(message))
    }
}