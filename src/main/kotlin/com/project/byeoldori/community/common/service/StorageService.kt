package com.project.byeoldori.community.common.service

import org.springframework.web.multipart.MultipartFile

interface StorageService {
    fun storeImage(file: MultipartFile): String  // 저장 후 공개 URL 반환
    fun deleteImageByUrl(url: String)

    // 교육 JSON 업로드 (공개 URL 반환)
    fun storeJson(file: MultipartFile): String

    // 게시글 첨부용 일반 파일 업로드 (문서 등, 공개 URL 반환)
    fun storeFile(file: MultipartFile): String
}