package com.project.byeoldori.community.common.util

import org.springframework.web.multipart.MultipartFile

object ImageMagicDetector {
    fun detect(file: MultipartFile): String? {
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
