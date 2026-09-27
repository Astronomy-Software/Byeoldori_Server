package com.project.byeoldori.community.comment.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CommentCreateRequest(
    @field:NotBlank(message = "내용은 비워둘 수 없습니다.")
    @field:Size(max = 1000, message = "댓글은 1000자를 넘을 수 없습니다.")
    val content: String,
    val parentId: Long? = null // null 이면 댓글, 값 있으면 대댓글
)

data class CommentResponse(
    val id: Long,
    val authorId: Long,
    val authorNickname: String?,
    val authorProfileImageUrl: String? = null,
    val content: String,
    val createdAt: LocalDateTime,
    val parentId: Long?,
    val depth: Int,
    val deleted: Boolean,
    val likeCount: Long,
    val liked: Boolean = false
)

data class CommentUpdateRequest(
    @field:NotBlank(message = "내용은 비워둘 수 없습니다.")
    @field:Size(max = 1000, message = "댓글은 1000자를 넘을 수 없습니다.")
    val content: String
)