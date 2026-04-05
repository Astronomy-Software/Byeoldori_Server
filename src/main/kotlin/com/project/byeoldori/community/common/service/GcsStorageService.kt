package com.project.byeoldori.community.common.service

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.StorageOptions
import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.InvalidInputException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.imageio.ImageIO

@Service
@ConditionalOnProperty(name = ["storage.type"], havingValue = "gcs")
class GcsStorageService(
    @Value("\${storage.gcs.bucket}") private val bucketName: String,
    @Value("\${storage.public-base-url}") private val publicBaseUrl: String,
    @Value("\${storage.allowed-content-types:image/jpeg,image/png,image/webp,image/gif}")
    private val allowedTypes: List<String>,
    @Value("\${storage.max-megapixels:50}") private val maxMegaPixels: Int
) : StorageService {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val storage = StorageOptions.getDefaultInstance().service

    override fun storeImage(file: MultipartFile): String {
        if (file.isEmpty) throw InvalidInputException("업로드할 파일이 비어있습니다.")

        val declared = (file.contentType ?: "").lowercase()
        if (declared.isNotBlank() && declared != "application/octet-stream" && declared !in allowedTypes) {
            throw InvalidInputException(ErrorCode.INVALID_FILE_TYPE.message + ": $declared")
        }

        val sniff = sniffMagic(file)
        if (sniff == null || sniff !in setOf("jpeg", "png", "webp", "gif")) {
            throw InvalidInputException("유효한 이미지 파일이 아닙니다.")
        }

        val img = ImageIO.read(file.inputStream) ?: throw InvalidInputException("이미지를 읽을 수 없습니다.")
        val mega = (img.width.toLong() * img.height.toLong()) / 1_000_000
        if (mega > maxMegaPixels) throw InvalidInputException(ErrorCode.FILE_TOO_LARGE.message)

        val datePath = LocalDate.now(ZoneId.of("Asia/Seoul")).toString().replace("-", "/")
        val filename = "${UUID.randomUUID()}.$sniff"
        val objectName = "images/$datePath/$filename"

        val blobInfo = BlobInfo.newBuilder(bucketName, objectName)
            .setContentType("image/$sniff")
            .build()

        file.inputStream.use { input ->
            storage.create(blobInfo, input.readBytes())
        }

        return "${publicBaseUrl.trimEnd('/')}/$objectName"
    }

    override fun deleteImageByUrl(url: String) {
        val base = publicBaseUrl.trimEnd('/')
        if (!url.startsWith(base)) {
            logger.warn("외부 URL이거나 형식이 잘못되어 삭제를 건너뜁니다: {}", url)
            return
        }
        try {
            val objectName = url.substring(base.length).trimStart('/')
            val deleted = storage.delete(bucketName, objectName)
            if (deleted) logger.info("GCS 파일 삭제 성공: {}", objectName)
            else logger.warn("GCS 파일이 존재하지 않습니다: {}", objectName)
        } catch (e: Exception) {
            logger.error("GCS 파일 삭제 오류 (URL: {}): {}", url, e.message)
        }
    }

    override fun storeJson(file: MultipartFile): String {
        if (file.isEmpty) throw InvalidInputException("빈 파일입니다.")
        val ct = (file.contentType ?: "").lowercase()
        if (ct != "application/json") throw InvalidInputException("application/JSON만 업로드할 수 있습니다.")

        val today = LocalDate.now()
        val datePath = "%d/%02d/%02d".format(today.year, today.monthValue, today.dayOfMonth)
        val filename = UUID.randomUUID().toString().replace("-", "") + ".json"
        val objectName = "json/$datePath/$filename"

        val blobInfo = BlobInfo.newBuilder(bucketName, objectName)
            .setContentType("application/json")
            .build()

        file.inputStream.use { input ->
            storage.create(blobInfo, input.readBytes())
        }

        return "${publicBaseUrl.trimEnd('/')}/$objectName"
    }

    private fun sniffMagic(file: MultipartFile): String? {
        file.inputStream.use { input ->
            val header = ByteArray(16)
            val read = input.read(header, 0, header.size)
            if (read < 12) return null
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return "jpeg"
            if (header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
                header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
                header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()) return "png"
            if (header.copyOfRange(0, 6).toString(Charsets.US_ASCII).startsWith("GIF8")) return "gif"
            val riff = String(header.copyOfRange(0, 4), Charsets.US_ASCII)
            val webp = String(header.copyOfRange(8, 12), Charsets.US_ASCII)
            if (riff == "RIFF" && webp == "WEBP") return "webp"
            return null
        }
    }
}
