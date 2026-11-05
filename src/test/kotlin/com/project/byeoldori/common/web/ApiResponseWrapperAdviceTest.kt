package com.project.byeoldori.common.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.core.MethodParameter
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.ResourceHttpMessageConverter
import org.springframework.http.converter.ResourceRegionHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse

/**
 * 성공 응답을 ApiResponse 봉투로 감싸는 advice. 이중 래핑과 문자열·바이트·리소스·ProblemDetail 통과를 확인한다.
 */
class ApiResponseWrapperAdviceTest {

    private val advice = ApiResponseWrapperAdvice()
    private val param = mock(MethodParameter::class.java)
    private val request = mock(ServerHttpRequest::class.java)
    private val response = mock(ServerHttpResponse::class.java)

    private fun write(body: Any?) =
        advice.beforeBodyWrite(body, param, MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter::class.java, request, response)

    @Test
    fun `JSON 변환기는 지원, 문자열·바이트·리소스 변환기는 지원하지 않는다`() {
        assertThat(advice.supports(param, MappingJackson2HttpMessageConverter::class.java)).isTrue()
        assertThat(advice.supports(param, StringHttpMessageConverter::class.java)).isFalse()
        assertThat(advice.supports(param, ByteArrayHttpMessageConverter::class.java)).isFalse()
        assertThat(advice.supports(param, ResourceHttpMessageConverter::class.java)).isFalse()
        assertThat(advice.supports(param, ResourceRegionHttpMessageConverter::class.java)).isFalse()
    }

    @Test
    fun `일반 객체는 ApiResponse_ok 로 감싼다`() {
        data class Dto(val id: Long)
        val out = write(Dto(7))
        assertThat(out).isEqualTo(ApiResponse.ok(Dto(7)))
        assertThat((out as ApiResponse<*>).success).isTrue()
        assertThat(out.message).isEqualTo("OK")
    }

    @Test
    fun `null 본문도 성공 봉투(data=null)로 감싼다`() {
        val out = write(null)
        assertThat(out).isInstanceOf(ApiResponse::class.java)
        assertThat((out as ApiResponse<*>).data).isNull()
        assertThat(out.success).isTrue()
    }

    @Test
    fun `이미 ApiResponse 면 그대로 둔다(이중 래핑 방지)`() {
        val already = ApiResponse.ok(listOf(1, 2))
        assertThat(write(already)).isSameAs(already)
        val failed = ApiResponse.fail<Unit>("오류", code = "E1")
        assertThat(write(failed)).isSameAs(failed)
    }

    @Test
    fun `ProblemDetail·Resource·ByteArray·String 은 감싸지 않는다`() {
        val problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST)
        assertThat(write(problem)).isSameAs(problem)

        val resource = ByteArrayResource(byteArrayOf(1, 2, 3))
        assertThat(write(resource)).isSameAs(resource)

        val bytes = byteArrayOf(9)
        assertThat(write(bytes)).isSameAs(bytes)

        assertThat(write("redirect:/x")).isEqualTo("redirect:/x")
    }

    @Test
    fun `컬렉션·맵은 감싼다`() {
        assertThat(write(listOf("a"))).isEqualTo(ApiResponse.ok(listOf("a")))
        assertThat(write(mapOf("k" to 1))).isEqualTo(ApiResponse.ok(mapOf("k" to 1)))
    }
}
