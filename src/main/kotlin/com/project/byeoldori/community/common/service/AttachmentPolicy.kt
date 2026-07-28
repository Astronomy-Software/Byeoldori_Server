package com.project.byeoldori.community.common.service

import com.project.byeoldori.common.exception.InvalidInputException
import org.springframework.web.multipart.MultipartFile

/**
 * 게시글 첨부용 일반 파일 업로드 정책.
 *
 * 이미지 업로드(storeImage)는 픽셀 폭탄까지 방어하는 별도 경로이고, 여기는 문서·압축 등
 * "그대로 보관해 다운로드시키는" 첨부를 다룬다. 임의 실행 파일 업로드를 막기 위해
 * 확장자 화이트리스트 + 크기 상한을 강제한다. Content-Type 은 조작이 쉬우므로 확장자를 신뢰선으로 둔다.
 */
object AttachmentPolicy {
    const val MAX_BYTES: Long = 20L * 1024 * 1024 // 20MB

    // 실행/스크립트 계열은 제외. 필요 시 확장.
    val ALLOWED_EXT: Set<String> = setOf(
        // 이미지
        "jpg", "jpeg", "png", "gif", "webp", "svg",
        // 문서
        "pdf", "txt", "csv", "md",
        "doc", "docx", "ppt", "pptx", "xls", "xlsx", "hwp", "hwpx",
        // 압축
        "zip",
    )

    /** 검증 후 저장에 쓸 안전한 확장자(소문자)를 반환한다. */
    fun validateAndExt(file: MultipartFile): String {
        if (file.isEmpty) throw InvalidInputException("업로드할 파일이 비어있습니다.")
        if (file.size > MAX_BYTES) {
            throw InvalidInputException("파일이 너무 큽니다. (최대 20MB)")
        }
        val name = file.originalFilename ?: ""
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext !in ALLOWED_EXT) {
            throw InvalidInputException("지원하지 않는 파일 형식입니다: ${if (ext.isBlank()) "(확장자 없음)" else ext}")
        }
        return ext
    }
}
