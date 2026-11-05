package com.project.byeoldori.common.logging

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * 요청마다 traceId 를 MDC 와 X-Trace-Id 헤더에 싣고, 처리 뒤(예외 포함) MDC 를 비운다.
 */
class MdcLoggingFilterTest {

    private val filter = MdcLoggingFilter()

    @AfterEach
    fun clear() = MDC.clear()

    @Test
    fun `체인 실행 중에는 MDC 에 traceId 가 있고 응답 헤더와 같다`() {
        val req = MockHttpServletRequest("GET", "/api/x")
        val res = MockHttpServletResponse()
        var seen: String? = null

        filter.doFilter(req, res, FilterChain { _, _ -> seen = MDC.get("traceId") })

        assertThat(seen).isNotNull().hasSize(8).matches("[0-9a-f]{8}")
        assertThat(res.getHeader("X-Trace-Id")).isEqualTo(seen)
    }

    @Test
    fun `체인이 끝나면 MDC 는 비워진다`() {
        filter.doFilter(MockHttpServletRequest("GET", "/"), MockHttpServletResponse(), FilterChain { _, _ -> })
        assertThat(MDC.get("traceId")).isNull()
    }

    @Test
    fun `체인이 예외를 던져도 MDC 는 비워지고 예외는 그대로 전파된다`() {
        assertThatThrownBy {
            filter.doFilter(MockHttpServletRequest("GET", "/"), MockHttpServletResponse(), FilterChain { _, _ -> throw IllegalStateException("boom") })
        }.isInstanceOf(IllegalStateException::class.java).hasMessage("boom")
        assertThat(MDC.get("traceId")).isNull()
    }

    @Test
    fun `요청마다 다른 traceId`() {
        val ids = (1..20).map {
            val res = MockHttpServletResponse()
            filter.doFilter(MockHttpServletRequest("GET", "/"), res, FilterChain { _, _ -> })
            res.getHeader("X-Trace-Id")!!
        }
        assertThat(ids.toSet()).hasSize(20)
    }

    @Test
    fun `같은 요청에 두 번 걸리지 않는다(OncePerRequestFilter)`() {
        val req = MockHttpServletRequest("GET", "/")
        val res = MockHttpServletResponse()
        var calls = 0
        filter.doFilter(req, res, FilterChain { r, s ->
            calls++
            // 내부 디스패치처럼 같은 요청으로 다시 필터를 거치면 체인만 호출돼야 한다
            filter.doFilter(r, s, FilterChain { _, _ -> calls++ })
        })
        assertThat(calls).isEqualTo(2)
        assertThat(res.getHeaders("X-Trace-Id")).hasSize(1)
    }
}
