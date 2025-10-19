package com.project.byeoldori.community.common.service

import com.project.byeoldori.common.exception.*
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun storeImage(file: MultipartFile): String {
        if (file.isEmpty) throw InvalidInputException("업로드할 파일이 비어있습니다.")

        // 1) MIME 화이트리스트 우선 체크
        val declared = (file.contentType ?: "").lowercase()
        if (declared.isNotBlank() && declared != "application/octet-stream" && declared !in allowedTypes) {
            throw InvalidInputException(ErrorCode.INVALID_FILE_TYPE.message + ": $declared")
        }

        // 2) 매직넘버 스니핑(간단 검증)
        val sniff = sniffMagic(file)
        if (sniff == null || sniff !in setOf("jpeg", "png", "webp", "gif")) {
            throw InvalidInputException("유효한 이미지 파일이 아닙니다.")
        }

        // 3) 이미지 픽셀 폭탄 방어 (옵션)
        val img = ImageIO.read(file.inputStream) ?: throw InvalidInputException("이미지를 읽을 수 없습니다.")
        val mega = (img.width.toLong() * img.height.toLong()) / 1_000_000
        if (mega > maxMegaPixels) {
            throw InvalidInputException(ErrorCode.FILE_TOO_LARGE.message)
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
        return listOf(publicBaseUrl.trimEnd('/'), datePath, filename).joinToString("/")
    }

    override fun deleteImageByUrl(url: String) {
        val baseUrl = publicBaseUrl.trimEnd('/')
        // 1. URL이 우리 서버의 URL(publicBaseUrl)로 시작하는지 확인
        if (!url.startsWith(baseUrl)) {
            logger.warn("외부 URL이거나 형식이 잘못되어 삭제를 건너뜁니다: {}", url)
            return
        }

        try {
            // 2. Public URL에서 상대 경로(relative path) 추출
            // e.g., "http://.../files/2025/10/19/uuid.jpg" -> "/2025/10/19/uuid.jpg"
            val relativePath = url.substring(baseUrl.length)

            // 3. BaseDir와 합쳐서 실제 파일 시스템 경로 생성
            // e.g., "uploads" + "/2025/10/19/uuid.jpg"
            val filePath = Paths.get(baseDir, relativePath).toAbsolutePath()

            // 4. 파일 시스템에서 삭제
            if (Files.exists(filePath)) {
                Files.delete(filePath)
                logger.info("파일 삭제 성공: {}", filePath)
            } else {
                logger.warn("삭제할 파일이 존재하지 않습니다: {}", filePath)
            }
        } catch (e: Exception) {
            // Path 조작 실패, 권한 문제 등
            logger.error("파일 삭제 중 오류 발생 (URL: {}): {}", url, e.message)
        }
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