package com.project.byeoldori.common.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 없는 경로·파일 요청이 500 이 아니라 404 로 응답되는지 (Spring 컨텍스트 불필요).
 * 예전에는 Exception 캐치올이 NoResourceFoundException 을 잡아 500 을 돌려줬다.
 */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()
    private val resolver = ExceptionHandlerMethodResolver(GlobalExceptionHandler::class.java)

    private val missingFile = NoResourceFoundException(HttpMethod.GET, "files/2020/01/01/nope.png")
    private val missingPath = NoHandlerFoundException("GET", "/nope", HttpHeaders())

    @Test
    fun `없는 파일·경로 예외는 캐치올이 아니라 404 핸들러로 간다`() {
        assertThat(resolver.resolveMethod(missingFile)?.name).isEqualTo("handleNotFound")
        assertThat(resolver.resolveMethod(missingPath)?.name).isEqualTo("handleNotFound")
    }

    @Test
    fun `없는 파일 요청은 404 와 고정 메시지를 돌려준다`() {
        val res = handler.handleNotFound(missingFile, MockHttpServletRequest("GET", "/files/2020/01/01/nope.png"))
        assertThat(res.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(res.body?.success).isFalse()
        assertThat(res.body?.message).isEqualTo("요청한 리소스를 찾을 수 없습니다.")
    }

    @Test
    fun `예상하지 못한 예외는 여전히 500 캐치올로 간다`() {
        assertThat(resolver.resolveMethod(IllegalStateException("boom"))?.name).isEqualTo("handleException")
    }
}
