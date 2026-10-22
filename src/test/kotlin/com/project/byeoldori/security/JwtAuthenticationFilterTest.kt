package com.project.byeoldori.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.service.CachedUserLookupService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.CannotCreateTransactionException

/**
 * JWT 필터의 실패 응답 구분: 토큰 문제는 401, 사용자 조회 중 DB 장애는 503.
 * 예전에는 DB 장애도 401 로 응답해, 웹이 재발급까지 실패하면 로그인 상태를 지워 사용자가 전부 로그아웃됐다.
 */
class JwtAuthenticationFilterTest {

    private val jwt = JwtUtil("test-secret-key-for-unit-tests-1234567890", accessExpMs = 3_600_000, refreshExpMs = 1_209_600_000)
    private val lookup = mock(CachedUserLookupService::class.java)
    private val filter = JwtAuthenticationFilter(jwt, lookup, ObjectMapper())

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    private fun run(token: String): Pair<MockHttpServletResponse, MockFilterChain> {
        val req = MockHttpServletRequest("GET", "/users/me").apply { addHeader("Authorization", "Bearer $token") }
        val res = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(req, res, chain)
        return res to chain
    }

    @Test
    fun `정상 토큰이면 인증을 등록하고 다음 필터로 넘긴다`() {
        val user = User(id = 1, email = "u@test.com", passwordHash = "x", name = "u", phone = "010")
        `when`(lookup.findByEmail("u@test.com")).thenReturn(user)
        val (res, chain) = run(jwt.generateAccessToken("u@test.com"))
        assertThat(res.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
        assertThat(SecurityContextHolder.getContext().authentication?.principal).isEqualTo(user)
    }

    @Test
    fun `사용자 조회 중 DB 장애는 401 이 아니라 503 으로 응답한다`() {
        `when`(lookup.findByEmail("u@test.com")).thenThrow(DataAccessResourceFailureException("db down"))
        val (res, chain) = run(jwt.generateAccessToken("u@test.com"))
        assertThat(res.status).isEqualTo(503)
        assertThat(chain.request).isNull()
    }

    @Test
    fun `커넥션을 얻지 못한 경우(트랜잭션 시작 실패)도 503 으로 응답한다`() {
        `when`(lookup.findByEmail("u@test.com")).thenThrow(CannotCreateTransactionException("no connection"))
        val (res, _) = run(jwt.generateAccessToken("u@test.com"))
        assertThat(res.status).isEqualTo(503)
    }

    @Test
    fun `refresh 토큰은 여전히 401 이고 위조 토큰은 인증 없이 통과시킨다(기존 동작)`() {
        assertThat(run(jwt.generateRefreshToken("u@test.com")).first.status).isEqualTo(401)

        val forged = JwtUtil("another-secret-key-for-unit-tests-0987654321", 3_600_000, 1_209_600_000)
            .generateAccessToken("u@test.com")
        val (res, chain) = run(forged)
        assertThat(res.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
        assertThat(SecurityContextHolder.getContext().authentication).isNull()
    }
}
