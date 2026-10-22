package com.project.byeoldori.community.common.service

import org.springframework.web.multipart.MultipartFile

interface StorageService {
    fun storeImage(file: MultipartFile): String  // 저장 후 공개 URL 반환
    fun deleteImageByUrl(url: String)

    /**
     * storeImage 가 만든 400px 썸네일의 공개 URL. 썸네일이 없으면(예전 업로드·생성 실패·외부 URL) null 이고,
     * 호출부는 원본 URL 로 대체한다.
     */
    fun thumbnailUrlOf(url: String): String? = null

    /**
     * 이 스토리지가 발급한 공개 URL 인지(외부 URL 이 아닌지).
     * 게시글 이미지에 외부 URL 을 받으면 목록을 보는 모든 사용자의 브라우저가 그 서버로 요청해
     * IP·User-Agent 가 노출되고(트래킹 픽셀), 업로드 검사(형식·크기·쿼터)도 우회된다.
     */
    fun isOwnUrl(url: String): Boolean

    // 교육 JSON 업로드 (공개 URL 반환)
    fun storeJson(file: MultipartFile): String

    // 게시글 첨부용 일반 파일 업로드 (문서 등, 공개 URL 반환)
    fun storeFile(file: MultipartFile): String

    companion object {
        private val SAFE_PATH = Regex("""^[A-Za-z0-9_./-]+$""")

        /**
         * url 이 prefix 로 시작하고, 나머지가 우리가 만드는 경로 문자(영숫자·_·-·.·/)로만 되어 있는지.
         * "../" 나 %2e%2e 로 브라우저가 경로를 정규화해 prefix 밖(다른 버킷 등)으로 나가는 URL 을 막는다.
         */
        fun isUnderPrefix(url: String, prefix: String): Boolean {
            if (!url.startsWith(prefix)) return false
            val rest = url.substring(prefix.length)
            return SAFE_PATH.matches(rest) && rest.split('/').none { it.isEmpty() || it == "." || it == ".." }
        }

        private val IMAGE_PATH = Regex("""^(\d{4}/\d{2}/\d{2})/([0-9A-Fa-f-]{36})\.(jpeg|png|webp|gif)$""")

        /**
         * 이미지 상대경로(yyyy/MM/dd/<uuid>.<ext>)에서 썸네일 상대경로(thumbnails/yyyy/MM/dd/<uuid>.jpg)를 정한다.
         * 썸네일 이름을 원본에서 정할 수 있어야 목록에서 쓰고, 원본을 지울 때 함께 지울 수 있다.
         * 이미지 형식이 아니면(첨부·json·썸네일 자신) null.
         */
        fun thumbnailPathOf(imageRelativePath: String): String? =
            IMAGE_PATH.matchEntire(imageRelativePath)?.let { m ->
                "thumbnails/${m.groupValues[1]}/${m.groupValues[2]}.jpg"
            }
    }
}
