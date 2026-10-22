package com.project.byeoldori.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.service.CachedUserLookupService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.MalformedJwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.dao.DataAccessException
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.transaction.TransactionException
import org.springframework.web.filter.OncePerRequestFilter
import io.jsonwebtoken.security.SignatureException
import java.nio.charset.StandardCharsets

@Component
class JwtAuthenticationFilter(
    private val jwtUtil: JwtUtil,  // JWT 생성/검증 유틸리티 주입
    private val cachedUserLookupService: CachedUserLookupService,
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
                    // 2. typ 클레임이 access인지 검증. refresh 토큰으로는 인증 거부.
                    if (!jwtUtil.isTokenType(token, "access")) {
                        logger.warn("access 토큰이 아닌 토큰으로 인증 시도 - IP: ${request.remoteAddr}")
                        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 토큰입니다.")
                        return
                    }
                    val userEmail = jwtUtil.extractEmail(token)
                    cachedUserLookupService.findByEmail(userEmail)
                        ?.takeIf { it.deletedAt == null }
                        ?.let { user ->
                            val authorities = user.roles.map { SimpleGrantedAuthority("ROLE_$it") }
                            val authentication = UsernamePasswordAuthenticationToken(
                                user, null, authorities
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
                // 사용자 조회 중 DB 장애(커넥션 풀 고갈·DB 재시작 등)는 토큰 문제가 아니다.
                // 401 로 답하면 웹이 재발급 실패 후 로그인 상태를 지워 버리므로 503 으로 구분한다.
                // (커넥션을 못 얻으면 DataAccessException 이 아니라 CannotCreateTransactionException 이 난다)
                if (e is DataAccessException || e is TransactionException) {
                    logger.error("JWT 인증 중 사용자 조회 실패(DB 장애) - IP: ${request.remoteAddr}", e)
                    sendErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "일시적인 서버 오류입니다. 잠시 후 다시 시도해주세요.")
                    return
                }
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