package com.project.byeoldori.community.common.dto

data class FileUploadResponse(
    val url: String,
    val filename: String,
    val size: Long,
    val contentType: String?
)