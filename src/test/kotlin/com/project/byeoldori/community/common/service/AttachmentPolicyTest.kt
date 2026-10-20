package com.project.byeoldori.community.common.service

import com.project.byeoldori.common.exception.InvalidInputException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

class AttachmentPolicyTest {

    private fun file(name: String, bytes: ByteArray = byteArrayOf(1, 2, 3)) =
        MockMultipartFile("file", name, "application/octet-stream", bytes)

    @Test
    fun `허용 확장자는 소문자로 정규화해 돌려준다`() {
        assertThat(AttachmentPolicy.validateAndExt(file("보고서.PDF"))).isEqualTo("pdf")
        assertThat(AttachmentPolicy.validateAndExt(file("사진.jpg"))).isEqualTo("jpg")
    }

    @Test
    fun `svg 와 실행·스크립트 계열, 확장자 없음, 이중 확장자는 거부한다`() {
        listOf("x.svg", "x.SVG", "x.html", "x.exe", "noext", "a.pdf.exe").forEach { name ->
            assertThatThrownBy { AttachmentPolicy.validateAndExt(file(name)) }
                .`as`(name).isInstanceOf(InvalidInputException::class.java)
        }
    }

    @Test
    fun `빈 파일과 20MB 초과는 거부한다`() {
        assertThatThrownBy { AttachmentPolicy.validateAndExt(file("a.pdf", ByteArray(0))) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThatThrownBy { AttachmentPolicy.validateAndExt(file("a.pdf", ByteArray((AttachmentPolicy.MAX_BYTES + 1).toInt()))) }
            .isInstanceOf(InvalidInputException::class.java)
    }
}
