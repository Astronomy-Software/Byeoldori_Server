package com.project.byeoldori.community.common.dto

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

fun <T> org.springframework.data.domain.Page<T>.toPageResponse(): PageResponse<T> =
    PageResponse(
        content = this.content,
        page = this.number,
        size = this.size,
        totalElements = this.totalElements,
        totalPages = this.totalPages
    )