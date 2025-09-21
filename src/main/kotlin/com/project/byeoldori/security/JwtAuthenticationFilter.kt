package com.project.byeoldori.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import io.jsonwebtoken.security.SignatureException

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil  // JWT 생성/검증 유틸리티 주입
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
        try {
            // 토큰이 있고 유효하다면 사용자 인증 처리
            if (token != null && jwtUtil.validateToken(token)) {
                val userEmail = jwtUtil.extractEmail(token)

                /**
                 * 인증 객체 생성
                 * - 첫 번째 인자: 인증된 사용자 이름 (이메일)
                 * - 두 번째 인자: 비밀번호 (null, 사용하지 않음)
                 * - 세 번째 인자: 권한 리스트 (비워둠)
                 */
                val authentication = UsernamePasswordAuthenticationToken(
                    userEmail, null, emptyList()
                )

                // 요청 정보에 인증 정보를 담아서 SecurityContext에 저장
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }
        }  catch (e: ExpiredJwtException) {
            logger.warn("Access Token 만료 - IP: ${request.remoteAddr}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access Token이 만료되었습니다.")
            return
        } catch (e: SignatureException) {
            logger.warn("JWT 서명 오류 발생 - IP: ${request.remoteAddr}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "잘못된 서명입니다.")
            return
        } catch (e: MalformedJwtException) {
            logger.warn("JWT 형식 오류 발생 - IP: ${request.remoteAddr}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "잘못된 토큰 형식입니다.")
            return
        } catch (e: Exception) {
            logger.error("알 수 없는 JWT 인증 오류 - IP: ${request.remoteAddr}")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "인증 실패")
            return
        }
        // 다음 필터로 요청을 전달
        filterChain.doFilter(request, response)
    }

    // 토큰 추출
    private fun extractToken(header: String?): String? {
        return if (header != null && header.startsWith("Bearer ")) {
            header.substring(7)
        } else null
    }
}