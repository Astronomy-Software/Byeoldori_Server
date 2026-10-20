package com.project.byeoldori.community.common.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * 썸네일은 원본과 같은 uuid 로 만들어 목록에서 쓰고, 원본을 지울 때 함께 지운다(고아 파일 방지).
 */
class LocalStorageServiceTest {

    @TempDir lateinit var dir: Path

    private val base = "http://localhost:8080"
    private fun service() = LocalStorageService(dir.toString(), base, listOf("image/png", "image/jpeg"), 50)

    private fun png(): MockMultipartFile {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", out)
        return MockMultipartFile("file", "a.png", "image/png", out.toByteArray())
    }

    @Test
    fun `썸네일 경로는 원본 경로에서 정해진다`() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        assertThat(StorageService.thumbnailPathOf("2026/10/20/$uuid.png")).isEqualTo("thumbnails/2026/10/20/$uuid.jpg")
        assertThat(StorageService.thumbnailPathOf("attach/2026/10/20/abc.pdf")).isNull()
        assertThat(StorageService.thumbnailPathOf("thumbnails/2026/10/20/$uuid.jpg")).isNull()
        assertThat(StorageService.thumbnailPathOf("../2026/10/20/$uuid.png")).isNull()
    }

    @Test
    fun `이미지 저장 시 같은 이름의 썸네일을 만들고, 원본 삭제 시 함께 지운다`() {
        val storage = service()
        val url = storage.storeImage(png())

        val thumbUrl = storage.thumbnailUrlOf(url)
        assertThat(thumbUrl).isNotNull()
        val thumbFile = dir.resolve(thumbUrl!!.removePrefix("$base/files/"))
        assertThat(Files.exists(thumbFile)).isTrue()
        assertThat(ImageIO.read(thumbFile.toFile()).width).isLessThanOrEqualTo(400)

        storage.deleteImageByUrl(url)
        assertThat(Files.exists(dir.resolve(url.removePrefix("$base/files/")))).isFalse()
        assertThat(Files.exists(thumbFile)).isFalse()
    }

    @Test
    fun `썸네일이 없는 예전 이미지·외부 URL 은 null`() {
        val storage = service()
        assertThat(storage.thumbnailUrlOf("$base/files/2026/01/01/123e4567-e89b-12d3-a456-426614174000.png")).isNull()
        assertThat(storage.thumbnailUrlOf("https://example.com/a.png")).isNull()
    }
}
