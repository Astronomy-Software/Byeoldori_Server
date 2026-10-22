package com.project.byeoldori.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration

/**
 * 레이트리밋 카운터는 INCR 와 TTL 설정을 한 번의 Lua 스크립트로 원자 실행해야 한다.
 * 예전에는 INCR 뒤 count == 1 일 때만 따로 EXPIRE 를 걸어, EXPIRE 가 실패하면 TTL 없는 키가 남아 영구 429 가 됐다.
 */
class RateLimitFilterTest {

    private val redis = mock(StringRedisTemplate::class.java)
    private val filter = RateLimitFilter(redis, ObjectMapper())

    private fun login(ip: String): MockHttpServletResponse {
        val req = MockHttpServletRequest("POST", "/auth/login").apply { remoteAddr = ip }
        val res = MockHttpServletResponse()
        filter.doFilter(req, res, MockFilterChain())
        return res
    }

    @Test
    fun `카운터는 스크립트 한 번으로 올리고 창 길이를 TTL 인자로 넘기며 한도를 넘으면 429`() {
        var count = 0L
        `when`(redis.execute(eq(RateLimitFilter.INCR_WITH_TTL), eq(listOf("rate:/auth/login:1.2.3.4")), eq("60")))
            .thenAnswer { ++count }

        repeat(10) { assertThat(login("1.2.3.4").status).isEqualTo(200) }
        assertThat(login("1.2.3.4").status).isEqualTo(429)

        // 따로 부르는 INCR·EXPIRE 경로는 더 이상 쓰지 않는다
        verify(redis, never()).opsForValue()
        verify(redis, never()).expire(anyString(), eq(Duration.ofMinutes(1)))
    }

    @Test
    fun `스크립트는 TTL 이 없을 때마다 EXPIRE 를 다시 걸어 TTL 없는 키도 복구한다`() {
        val script = RateLimitFilter.INCR_WITH_TTL.scriptAsString
        assertThat(script).contains("redis.call('INCR', KEYS[1])")
        assertThat(script).contains("redis.call('TTL', KEYS[1]) < 0")
        assertThat(script).contains("redis.call('EXPIRE', KEYS[1], ARGV[1])")
        assertThat(RateLimitFilter.INCR_WITH_TTL.resultType).isEqualTo(java.lang.Long::class.java)
    }

    @Test
    fun `글자를 퍼센트 인코딩하거나 끝 슬래시·대소문자를 바꿔도 같은 규칙과 같은 카운터 키로 센다`() {
        var count = 0L
        `when`(redis.execute(eq(RateLimitFilter.INCR_WITH_TTL), eq(listOf("rate:/auth/login:5.6.7.8")), eq("60")))
            .thenAnswer { ++count }

        val variants = listOf("/auth/login", "/auth/%6Cogin", "/auth/%6c%6f%67%69%6e", "/auth/login/", "/auth/login;x=1", "/Auth/Login")
        repeat(10) { i ->
            val req = MockHttpServletRequest("POST", variants[i % variants.size]).apply { remoteAddr = "5.6.7.8" }
            val res = MockHttpServletResponse()
            filter.doFilter(req, res, MockFilterChain())
            assertThat(res.status).isEqualTo(200)
        }

        val req = MockHttpServletRequest("POST", "/auth/%6Cogin").apply { remoteAddr = "5.6.7.8" }
        val res = MockHttpServletResponse()
        filter.doFilter(req, res, MockFilterChain())
        assertThat(res.status).isEqualTo(429)
        assertThat(count).isEqualTo(11)
    }

    @Test
    fun `규칙이 없는 경로는 Redis 를 건드리지 않는다`() {
        val req = MockHttpServletRequest("GET", "/posts")
        val res = MockHttpServletResponse()
        filter.doFilter(req, res, MockFilterChain())
        assertThat(res.status).isEqualTo(200)
        verify(redis, never()).opsForValue()
    }
}
