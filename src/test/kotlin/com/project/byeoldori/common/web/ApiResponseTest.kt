package com.project.byeoldori.common.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * ApiResponse 팩토리 메서드 검증 (순수 data class, Spring 컨텍스트 불필요).
 */
class ApiResponseTest {

    @Test
    fun `ok()는 기본 성공 응답을 만든다`() {
        val res = ApiResponse.ok()

        assertThat(res.success).isTrue()
        assertThat(res.message).isEqualTo("OK")
        assertThat(res.data).isNull()
        assertThat(res.code).isNull()
    }

    @Test
    fun `ok(message)는 메시지를 설정하고 데이터는 비운다`() {
        val res = ApiResponse.ok("처리 완료")

        assertThat(res.success).isTrue()
        assertThat(res.message).isEqualTo("처리 완료")
        assertThat(res.data).isNull()
    }

    @Test
    fun `ok(data)는 데이터를 담은 성공 응답을 만든다`() {
        val res = ApiResponse.ok(42)

        assertThat(res.success).isTrue()
        assertThat(res.message).isEqualTo("OK")
        assertThat(res.data).isEqualTo(42)
    }

    @Test
    fun `fail()은 실패 응답을 만들고 code를 담는다`() {
        val res = ApiResponse.fail<String>("에러 발생", data = "detail", code = "E001")

        assertThat(res.success).isFalse()
        assertThat(res.message).isEqualTo("에러 발생")
        assertThat(res.data).isEqualTo("detail")
        assertThat(res.code).isEqualTo("E001")
    }

    @Test
    fun `fail()의 data와 code는 기본적으로 null이다`() {
        val res = ApiResponse.fail<Unit>("에러")

        assertThat(res.success).isFalse()
        assertThat(res.message).isEqualTo("에러")
        assertThat(res.data).isNull()
        assertThat(res.code).isNull()
    }
}
