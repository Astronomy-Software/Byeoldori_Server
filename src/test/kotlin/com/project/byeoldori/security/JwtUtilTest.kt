package com.project.byeoldori.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * JwtUtil 검증 (순수 인스턴스화, Spring 컨텍스트 불필요).
 * 생성자: (secret, accessExpMs, refreshExpMs)
 */
class JwtUtilTest {

    private val secret = "test-secret-key-for-unit-tests-1234567890"
    private val jwt = JwtUtil(secret, accessExpMs = 3_600_000, refreshExpMs = 1_209_600_000)

    @Test
    fun `access 토큰은 생성 후 검증에 성공한다`() {
        val token = jwt.generateAccessToken("user@example.com")
        assertThat(jwt.validateToken(token)).isTrue()
        assertThat(jwt.extractEmail(token)).isEqualTo("user@example.com")
    }

    @Test
    fun `access 토큰의 타입은 access이다`() {
        val token = jwt.generateAccessToken("user@example.com")
        assertThat(jwt.isTokenType(token, "access")).isTrue()
        assertThat(jwt.isTokenType(token, "refresh")).isFalse()
    }

    @Test
    fun `refresh 토큰의 타입은 refresh이다`() {
        val token = jwt.generateRefreshToken("user@example.com")
        assertThat(jwt.validateToken(token)).isTrue()
        assertThat(jwt.isTokenType(token, "refresh")).isTrue()
        assertThat(jwt.isTokenType(token, "access")).isFalse()
    }

    @Test
    fun `만료된 토큰은 검증에 실패한다`() {
        // 음수 TTL → 발급 시점에 이미 만료
        val expiredJwt = JwtUtil(secret, accessExpMs = -1_000, refreshExpMs = -1_000)
        val token = expiredJwt.generateAccessToken("user@example.com")
        assertThat(expiredJwt.validateToken(token)).isFalse()
    }

    @Test
    fun `변조된 토큰은 검증에 실패한다`() {
        val token = jwt.generateAccessToken("user@example.com")
        // 마지막 문자를 바꿔 서명 무효화
        val tampered = token.dropLast(1) + if (token.last() == 'A') 'B' else 'A'
        assertThat(jwt.validateToken(tampered)).isFalse()
    }

    @Test
    fun `형식이 잘못된 토큰은 검증에 실패한다`() {
        assertThat(jwt.validateToken("not-a-jwt")).isFalse()
        assertThat(jwt.validateToken("")).isFalse()
    }

    @Test
    fun `다른 시크릿으로 발급한 토큰은 검증에 실패한다`() {
        val other = JwtUtil("completely-different-secret-value-0987654321", 3_600_000, 1_209_600_000)
        val token = other.generateAccessToken("user@example.com")
        assertThat(jwt.validateToken(token)).isFalse()
    }

    @Test
    fun `발급한 토큰의 만료시각을 추출할 수 있다`() {
        val token = jwt.generateAccessToken("user@example.com")
        val exp = jwt.extractExpiration(token)
        assertThat(exp).isAfter(java.time.LocalDateTime.now(jwt.zoneId).minusMinutes(1))
    }
}
