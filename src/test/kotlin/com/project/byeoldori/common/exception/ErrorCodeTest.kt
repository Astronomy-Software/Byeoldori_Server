package com.project.byeoldori.common.exception

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * ErrorCode enum 무결성 검증 (순수, Spring 컨텍스트 불필요).
 */
class ErrorCodeTest {

    @Test
    fun `모든 코드는 비어있지 않은 메시지와 status를 가진다`() {
        ErrorCode.entries.forEach { code ->
            assertThat(code.message).`as`("%s 메시지", code.name).isNotBlank()
            assertThat(code.status).`as`("%s status", code.name).isNotNull()
        }
    }

    @Test
    fun `특수 케이스 status 매핑이 유지된다`() {
        assertThat(ErrorCode.OUT_OF_SERVICE_AREA.status).isEqualTo(HttpStatus.I_AM_A_TEAPOT)
        assertThat(ErrorCode.FILE_TOO_LARGE.status).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.status).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        assertThat(ErrorCode.UNAUTHORIZED.status).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(ErrorCode.USER_NOT_FOUND.status).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `enum 이름은 중복되지 않는다`() {
        val names = ErrorCode.entries.map { it.name }
        assertThat(names).doesNotHaveDuplicates()
    }

    @Test
    fun `valueOf 로 상수를 되찾을 수 있다`() {
        assertThat(ErrorCode.valueOf("USER_NOT_FOUND")).isEqualTo(ErrorCode.USER_NOT_FOUND)
    }
}
