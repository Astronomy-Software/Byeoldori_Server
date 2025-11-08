package com.project.byeoldori.community.common.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.community.common.dto.FileUploadResponse
import com.project.byeoldori.community.common.service.StorageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/files")
@Tag(name = "File", description = "이미지/JSON 업로드 API (로컬)")
class FileController(
    private val storage: StorageService
) {
    @PostMapping("/image", consumes = ["multipart/form-data"])
    @Operation(summary = "이미지 업로드", description = "단일 이미지 업로드 후 공개 URL 반환")
    fun upload(@RequestPart("file") file: MultipartFile): ApiResponse<FileUploadResponse> {
        val url = storage.storeImage(file)
        val resp = FileUploadResponse(url, file.originalFilename ?: "", file.size, file.contentType)
        return ApiResponse.ok(resp)
    }

    @PostMapping("/json", consumes = ["multipart/form-data"])
    @Operation(summary = "교육 JSON 업로드", description = "교육 콘텐츠 JSON 파일 업로드 후 공개 URL 반환")
    fun uploadJson(@RequestPart("file") file: MultipartFile): ApiResponse<FileUploadResponse> {
        val url = storage.storeJson(file)
        val resp = FileUploadResponse(url, file.originalFilename ?: "", file.size, file.contentType)
        return ApiResponse.ok(resp)
    }
}