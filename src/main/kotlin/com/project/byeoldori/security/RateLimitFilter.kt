package com.project.byeoldori.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.common.web.ApiResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
class RateLimitFilter(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(this::class.java)

    data class RateRule(val limit: Long, val window: Duration)

    private val rules = mapOf(
        "/auth/login"                   to RateRule(10,  Duration.ofMinutes(1)),
        "/auth/signup"                  to RateRule(5,   Duration.ofMinutes(1)),
        "/auth/password/reset-request"  to RateRule(3,   Duration.ofHours(1)),
        "/auth/find-email"              to RateRule(10,  Duration.ofMinutes(1)),
        "/auth/google"                  to RateRule(10,  Duration.ofMinutes(1)),
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val rule = rules[request.requestURI] ?: run {
            filterChain.doFilter(request, response)
            return
        }

        val ip = resolveClientIp(request)
        val key = "rate:${request.requestURI}:$ip"

        val count = redisTemplate.opsForValue().increment(key) ?: 1L
        if (count == 1L) {
            redisTemplate.expire(key, rule.window)
        }

        if (count > rule.limit) {
            log.warn("Rate limit 초과 - IP: $ip, URI: ${request.requestURI} ($count/${rule.limit})")
            sendTooManyRequests(response)
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        return request.getHeader("X-Forwarded-For")?.split(",")?.first()?.trim()
            ?: request.getHeader("X-Real-IP")
            ?: request.remoteAddr
    }

    private fun sendTooManyRequests(response: HttpServletResponse) {
        response.status = 429
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(response.writer, ApiResponse.fail<Unit>("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."))
    }
}
