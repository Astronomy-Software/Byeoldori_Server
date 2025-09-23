package com.project.byeoldori.community.common.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.*
import javax.imageio.ImageIO

@Service
@ConditionalOnProperty(name = ["storage.type"], havingValue = "local", matchIfMissing = true)
class LocalStorageService(
    @Value("\${storage.local.base-dir}") private val baseDir: String,
    @Value("\${storage.public-base-url}") private val publicBaseUrl: String,
    @Value("\${storage.allowed-content-types:image/jpeg,image/png,image/webp,image/gif}")
    private val allowedTypes: List<String>,
    @Value("\${storage.max-megapixels:50}") private val maxMegaPixels: Int
) : StorageService {

    override fun storeImage(file: MultipartFile): String {
        if (file.isEmpty) throw IllegalArgumentException("Empty file")

        // 1) MIME 화이트리스트 우선 체크
        val declared = (file.contentType ?: "").lowercase()
        if (declared.isNotBlank() && declared != "application/octet-stream" && declared !in allowedTypes) {
            throw IllegalArgumentException("Unsupported content-type: $declared")
        }

        // 2) 매직넘버 스니핑(간단 검증)
        val sniff = sniffMagic(file)
        if (sniff == null || sniff !in setOf("jpeg","png","webp","gif")) {
            throw IllegalArgumentException("Invalid image file")
        }

        // 3) 이미지 픽셀 폭탄 방어 (옵션)
        val img = ImageIO.read(file.inputStream) ?: throw IllegalArgumentException("Unreadable image")
        val mega = (img.width.toLong() * img.height.toLong()) / 1_000_000
        if (mega > maxMegaPixels) {
            throw IllegalArgumentException("Image too large (> ${maxMegaPixels}MP)")
        }

        // 4) 저장 경로(날짜 폴더)
        val datePath = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString().replace("-", "/")
        val dir = Paths.get(baseDir, datePath)
        Files.createDirectories(dir)

        // 5) 안전한 파일명(UUID + sniff 확장자)
        val filename = "${UUID.randomUUID()}.$sniff"
        val target = dir.resolve(filename)

        // MultipartFile 스트림은 한 번 읽으면 소진되므로, 위에서 read한 후 다시 InputStream을 열어야 함
        file.inputStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }

        // 6) 공개 URL 조합
        val url = listOf(publicBaseUrl.trimEnd('/'), datePath, filename).joinToString("/")
        return url
    }

    private fun sniffMagic(file: MultipartFile): String? {
        file.inputStream.use { input ->
            val header = ByteArray(16)
            val read = input.read(header, 0, header.size)
            if (read < 12) return null
            // JPEG: FF D8
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return "jpeg"
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
                header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
                header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()) return "png"
            // GIF: "GIF87a" or "GIF89a"
            if (header.copyOfRange(0,6).toString(Charsets.US_ASCII).startsWith("GIF8")) return "gif"
            // WEBP: "RIFF"...."WEBP"
            val riff = String(header.copyOfRange(0,4), Charsets.US_ASCII)
            val webp = String(header.copyOfRange(8,12), Charsets.US_ASCII)
            if (riff == "RIFF" && webp == "WEBP") return "webp"
            return null
        }
    }
}