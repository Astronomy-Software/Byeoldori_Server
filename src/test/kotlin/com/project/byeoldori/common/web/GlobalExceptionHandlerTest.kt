package com.project.byeoldori.common.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
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

    // ── 아래는 예전에 모두 캐치올(500)로 가던 클라이언트 측 오류들 ──

    @Test
    fun `PreAuthorize 거부는 500 이 아니라 403`() {
        val denied = AuthorizationDeniedException("Access Denied", AuthorizationDecision(false))
        assertThat(resolver.resolveMethod(denied)?.name).isEqualTo("handleAccessDenied")

        val res = handler.handleAccessDenied(denied, MockHttpServletRequest("POST", "/observationsites"))
        assertThat(res.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(res.body?.code).isEqualTo("FORBIDDEN")
    }

    @Test
    fun `지원하지 않는 메서드는 405 와 Allow 헤더`() {
        val e = HttpRequestMethodNotSupportedException("GET", listOf("POST"))
        assertThat(resolver.resolveMethod(e)?.name).isEqualTo("handleUnsupported")

        val res = handler.handleUnsupported(e, MockHttpServletRequest("GET", "/auth/login"))
        assertThat(res.statusCode).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
        assertThat(res.headers.allow).containsExactly(HttpMethod.POST)
        assertThat(res.body?.success).isFalse()
    }

    @Test
    fun `지원하지 않는 Content-Type 은 415`() {
        val e = HttpMediaTypeNotSupportedException(MediaType.APPLICATION_JSON, listOf(MediaType.MULTIPART_FORM_DATA))
        assertThat(resolver.resolveMethod(e)?.name).isEqualTo("handleUnsupported")

        val res = handler.handleUnsupported(e, MockHttpServletRequest("POST", "/files/image"))
        assertThat(res.statusCode).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    }

    @Test
    fun `응답 형식 협상 실패는 본문 없는 406`() {
        val e = HttpMediaTypeNotAcceptableException(listOf(MediaType.APPLICATION_JSON))
        assertThat(resolver.resolveMethod(e)?.name).isEqualTo("handleNotAcceptable")

        val res = handler.handleNotAcceptable(e, MockHttpServletRequest("GET", "/observationsites"))
        assertThat(res.statusCode).isEqualTo(HttpStatus.NOT_ACCEPTABLE)
        assertThat(res.body).isNull()
    }

    @Test
    fun `DB 제약 위반은 409 와 고정 메시지(SQL·제약 이름 비노출)`() {
        val e = DataIntegrityViolationException("Duplicate entry '' for key 'users.UK2ty1xmrrgtn89xt7kyxx6ta7h'")
        assertThat(resolver.resolveMethod(e)?.name).isEqualTo("handleDataIntegrity")

        val res = handler.handleDataIntegrity(e, MockHttpServletRequest("PATCH", "/users/me"))
        assertThat(res.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(res.body?.message).doesNotContain("Duplicate", "UK2ty")
    }

    @Test
    fun `multipart 파트 누락·깨진 multipart 는 400, 용량 초과는 그대로 413`() {
        val missingPart = MissingServletRequestPartException("file")
        val broken = MultipartException("Failed to parse multipart servlet request")
        assertThat(resolver.resolveMethod(missingPart)?.name).isEqualTo("handleBadRequest")
        assertThat(resolver.resolveMethod(broken)?.name).isEqualTo("handleBadRequest")
        assertThat(resolver.resolveMethod(MaxUploadSizeExceededException(1L))?.name).isEqualTo("handleMaxUpload")

        val res = handler.handleBadRequest(missingPart, MockHttpServletRequest("POST", "/files/image"))
        assertThat(res.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(res.body?.message).isEqualTo("요청 값이 올바르지 않습니다.")
    }
}
