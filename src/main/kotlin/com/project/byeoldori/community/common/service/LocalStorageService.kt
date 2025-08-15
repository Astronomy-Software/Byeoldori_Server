package com.project.byeoldori.community.common.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.*

@Service
@ConditionalOnProperty(name = ["storage.type"], havingValue = "local", matchIfMissing = true)
class LocalStorageService(
    @Value("\${storage.local.base-dir}") private val baseDir: String,
    @Value("\${storage.public-base-url}") private val publicBaseUrl: String
) : StorageService {

    private val allowed = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    override fun storeImage(file: MultipartFile): String {
        require(!file.isEmpty) { "빈 파일은 업로드할 수 없습니다." }
        val type = file.contentType ?: "application/octet-stream"
        require(type in allowed) { "허용되지 않은 이미지 형식입니다: $type" }

        val d = LocalDate.now()
        val datePath = "%04d/%02d/%02d".format(d.year, d.monthValue, d.dayOfMonth)
        val dir = Paths.get(baseDir, datePath)
        Files.createDirectories(dir)

        val ext = when (type) {
            "image/jpeg" -> "jpg"
            "image/png"  -> "png"
            "image/webp" -> "webp"
            "image/gif"  -> "gif"
            else -> StringUtils.getFilenameExtension(file.originalFilename ?: "") ?: "bin"
        }

        val filename = "${UUID.randomUUID()}.$ext"
        val target = dir.resolve(filename)
        file.inputStream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }

        return listOf(publicBaseUrl.trimEnd('/'), datePath, filename).joinToString("/")
    }
}