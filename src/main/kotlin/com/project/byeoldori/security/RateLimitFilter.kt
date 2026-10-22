package com.project.byeoldori.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.common.web.ApiResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.script.RedisScript
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
        "/auth/kakao"                   to RateRule(10,  Duration.ofMinutes(1)),
        "/auth/naver"                   to RateRule(10,  Duration.ofMinutes(1)),
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

        // INCR 와 EXPIRE 를 한 번의 Lua 스크립트로 원자 실행한다. 따로 호출하면 INCR 만 성공하고 EXPIRE 가
        // 실패(일시 오류·종료)했을 때 TTL 없는 키가 남아 그 IP(웹은 Worker IP 공유)가 영구히 429 를 받았다.
        // TTL 이 없으면(-1) 매번 다시 걸어, 예전에 남은 TTL 없는 키도 스스로 복구된다.
        val count = redisTemplate.execute(INCR_WITH_TTL, listOf(key), rule.window.seconds.toString()) ?: 1L

        if (count > rule.limit) {
            log.warn("Rate limit 초과 - IP: $ip, URI: ${request.requestURI} ($count/${rule.limit})")
            sendTooManyRequests(response)
            return
        }

        filterChain.doFilter(request, response)
    }

    // 신뢰 프록시(리버스 프록시)가 XFF를 세팅한다는 가정 하에, 첫 값의 형식을 검증해서 사용.
    // 형식이 유효하지 않거나 헤더가 없으면 remoteAddr로 폴백(스푸핑 방지).
    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && isValidIp(it) }
        if (forwarded != null) return forwarded

        val realIp = request.getHeader("X-Real-IP")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && isValidIp(it) }
        if (realIp != null) return realIp

        return request.remoteAddr
    }

    private fun isValidIp(ip: String): Boolean =
        IPV4_REGEX.matches(ip) || IPV6_REGEX.matches(ip)

    companion object {
        internal val INCR_WITH_TTL: RedisScript<Long> = DefaultRedisScript(
            """
            local c = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return c
            """.trimIndent(),
            Long::class.javaObjectType
        )

        // 형식 검증용(엄밀한 범위 검증이 아닌 스푸핑성 임의 문자열 차단 목적)
        private val IPV4_REGEX = Regex("^(\\d{1,3})(\\.\\d{1,3}){3}\$")
        private val IPV6_REGEX = Regex("^[0-9A-Fa-f:]+\$")
    }

    private fun sendTooManyRequests(response: HttpServletResponse) {
        response.status = 429
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        objectMapper.writeValue(response.writer, ApiResponse.fail<Unit>("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."))
    }
}
