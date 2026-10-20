package com.project.byeoldori.community.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

class ImageMagicDetectorTest {

    private fun detect(header: ByteArray) =
        ImageMagicDetector.detect(MockMultipartFile("file", "x", null, header + ByteArray(16)))

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `매직넘버로 래스터 이미지 형식을 판정한다`() {
        assertThat(detect(bytes(0xFF, 0xD8, 0xFF))).isEqualTo("jpeg")
        assertThat(detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))).isEqualTo("png")
        assertThat(detect("GIF89a".toByteArray())).isEqualTo("gif")
        assertThat(detect("RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray())).isEqualTo("webp")
    }

    @Test
    fun `svg·pdf 텍스트와 12바이트 미만은 이미지가 아니다`() {
        assertThat(detect("<svg xmlns=\"http://www.w3.org/2000/svg\">".toByteArray())).isNull()
        assertThat(detect("%PDF-1.7".toByteArray())).isNull()
        assertThat(ImageMagicDetector.detect(MockMultipartFile("file", "x", null, ByteArray(5)))).isNull()
    }
}
