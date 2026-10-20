package com.project.byeoldori.community.common.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.community.common.dto.FileUploadResponse
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.community.common.service.UploadedFileService
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/files")
@Tag(name = "File", description = "이미지·파일 업로드 API (로컬)")
class FileController(
    private val storage: StorageService,
    private val uploadedFiles: UploadedFileService,
) {
    // 업로드마다 사용자별 쿼터(시간당 개수·24시간 용량)를 확인하고, 업로더를 기록한다.
    // 기록은 게시글·일정에서 파일을 지울 때 "본인이 올린 파일인지" 확인하는 근거가 된다.
    @PostMapping("/image", consumes = ["multipart/form-data"])
    @Operation(summary = "이미지 업로드", description = "단일 이미지 업로드 후 공개 URL 반환")
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<FileUploadResponse> {
        uploadedFiles.checkQuota(user.id, file.size)
        val url = storage.storeImage(file)
        uploadedFiles.record(url, user.id, file.size)
        val resp = FileUploadResponse(url, file.originalFilename ?: "", file.size, file.contentType)
        return ApiResponse.ok(resp)
    }

    @PostMapping("/upload", consumes = ["multipart/form-data"])
    @Operation(summary = "일반 파일 업로드", description = "게시글 첨부용 문서/파일 업로드 후 공개 URL 반환(확장자 화이트리스트·20MB 제한)")
    fun uploadFile(
        @RequestPart("file") file: MultipartFile,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<FileUploadResponse> {
        uploadedFiles.checkQuota(user.id, file.size)
        val url = storage.storeFile(file)
        uploadedFiles.record(url, user.id, file.size)
        val resp = FileUploadResponse(url, file.originalFilename ?: "", file.size, file.contentType)
        return ApiResponse.ok(resp)
    }
}