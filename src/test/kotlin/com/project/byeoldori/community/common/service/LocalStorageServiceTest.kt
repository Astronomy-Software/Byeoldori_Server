package com.project.byeoldori.community.common.service

import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.community.common.util.ImageHeaderReader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32
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

    /** 800x600 JPEG 에 EXIF Orientation=6(시계방향 90도 회전해서 보여야 함)을 넣는다 — 폰 세로 사진과 같은 형태 */
    private fun jpegWithOrientation6(): MockMultipartFile {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "jpeg", out)
        val jpeg = out.toByteArray()
        val tiff = byteArrayOf(
            0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,             // 빅엔디언 TIFF 헤더, IFD0 오프셋 8
            0x00, 0x01,                                                 // 엔트리 1개
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00, // Orientation(SHORT)=6
            0x00, 0x00, 0x00, 0x00                                      // 다음 IFD 없음
        )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val len = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + payload
        // SOI(2) 뒤의 JFIF APP0 세그먼트 다음에 APP1(Exif)을 끼워 넣는다
        val app0End = 4 + (((jpeg[4].toInt() and 0xFF) shl 8) or (jpeg[5].toInt() and 0xFF))
        val bytes = jpeg.copyOfRange(0, app0End) + app1 + jpeg.copyOfRange(app0End, jpeg.size)
        return MockMultipartFile("file", "portrait.jpg", "image/jpeg", bytes)
    }

    @Test
    fun `EXIF Orientation 이 있는 사진은 썸네일에서도 회전이 반영된다`() {
        val storage = service()
        val url = storage.storeImage(jpegWithOrientation6())

        val thumbFile = dir.resolve(storage.thumbnailUrlOf(url)!!.removePrefix("$base/files/"))
        val thumb = ImageIO.read(thumbFile.toFile())
        // 원본은 가로(800x600)지만 Orientation=6 이라 세로로 보여야 한다 → 300x400
        assertThat(thumb.height).isGreaterThan(thumb.width)
        assertThat(thumb.height).isEqualTo(400)
    }

    @Test
    fun `썸네일이 없는 예전 이미지·외부 URL 은 null`() {
        val storage = service()
        assertThat(storage.thumbnailUrlOf("$base/files/2026/01/01/123e4567-e89b-12d3-a456-426614174000.png")).isNull()
        assertThat(storage.thumbnailUrlOf("https://example.com/a.png")).isNull()
    }

    /** 작은 PNG 의 IHDR 가로·세로만 바꿔 "헤더상 거대한" 이미지를 만든다(실제 픽셀 데이터는 그대로 작다) */
    private fun pngWithHeaderSize(width: Int, height: Int): MockMultipartFile {
        val bytes = png().bytes.copyOf()
        // 시그니처(8) + 길이(4) + "IHDR"(4) 다음이 width(4), height(4)
        ByteBuffer.wrap(bytes, 16, 8).putInt(width).putInt(height)
        val crc = CRC32().apply { update(bytes, 12, 4 + 13) }.value.toInt()
        ByteBuffer.wrap(bytes, 12 + 4 + 13, 4).putInt(crc)
        return MockMultipartFile("file", "bomb.png", "image/png", bytes)
    }

    @Test
    fun `헤더 크기만 읽고, 픽셀 수 상한을 넘는 이미지는 디코딩 전에 거부한다`() {
        assertThat(ImageHeaderReader.readSize(png())).isEqualTo(800 to 600)

        val bomb = pngWithHeaderSize(20_000, 20_000)
        assertThat(ImageHeaderReader.readSize(bomb)).isEqualTo(20_000 to 20_000)
        assertThatThrownBy { service().storeImage(bomb) }.isInstanceOf(InvalidInputException::class.java)
        // 거부된 업로드는 디스크에 아무것도 남기지 않는다
        assertThat(Files.list(dir).use { it.count() }).isZero()
    }

    // 1x1 무손실(VP8L) WebP. JDK ImageIO 만으로는 리더가 없어 항상 "이미지를 읽을 수 없습니다"가 났다
    private val webp1x1 = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==")

    @Test
    fun `WebP 는 헤더로 크기를 읽고 원본과 썸네일을 저장한다`() {
        val file = MockMultipartFile("file", "a.webp", "image/webp", webp1x1)
        assertThat(ImageHeaderReader.readSize(file)).isEqualTo(1 to 1)

        val storage = LocalStorageService(dir.toString(), base, listOf("image/webp"), 50)
        val url = storage.storeImage(file)
        assertThat(url).endsWith(".webp")
        assertThat(Files.exists(dir.resolve(url.removePrefix("$base/files/")))).isTrue()
        assertThat(storage.thumbnailUrlOf(url)).isNotNull()
    }

    @Test
    fun `헤더상 픽셀 수 상한을 넘는 WebP 도 디코딩 전에 거부한다`() {
        // VP8L 헤더(시그니처 0x2F 다음 4바이트, 리틀엔디언)의 가로·세로를 각각 16384 로 바꾼다
        val bytes = webp1x1.copyOf()
        ByteBuffer.wrap(bytes, 21, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(0x3FFF or (0x3FFF shl 14) or (1 shl 28))
        val bomb = MockMultipartFile("file", "bomb.webp", "image/webp", bytes)
        assertThat(ImageHeaderReader.readSize(bomb)).isEqualTo(16_384 to 16_384)
        assertThatThrownBy { LocalStorageService(dir.toString(), base, listOf("image/webp"), 50).storeImage(bomb) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThat(Files.list(dir).use { it.count() }).isZero()
    }
}
