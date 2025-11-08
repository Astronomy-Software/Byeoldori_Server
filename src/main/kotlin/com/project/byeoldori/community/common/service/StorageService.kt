package com.project.byeoldori.community.common.service

import org.springframework.web.multipart.MultipartFile

interface StorageService {
    fun storeImage(file: MultipartFile): String  // 저장 후 공개 URL 반환
    fun deleteImageByUrl(url: String)

    // 교육 JSON 업로드 (공개 URL 반환)
    fun storeJson(file: MultipartFile): String
}