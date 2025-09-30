package com.project.byeoldori.community.post.dto

import com.project.byeoldori.community.common.domain.*
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class ReviewDto(
    val location: String? = null,
    val target: String? = null,
    val equipment: String? = null,
    val observationDate: String? = null,
    @field:Min(1) @field:Max(5)
    val score: Int? = null // 1~5점
)

data class EducationDto(
    val summary: String? = null,
    val difficulty: EducationDifficulty? = null,
    val tags: String? = null,
    val status: EducationStatus? = null // null → DRAFT
)

data class PostCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 120) val title: String,

    @field:NotBlank(message = "본문은 필수입니다.")
    val content: String,
    val review: ReviewDto? = null,
    val education: EducationDto? = null,
    val imageUrls: List<String>? = emptyList()
)

data class PostUpdateRequest(
    @field:Size(max = 120) val title: String? = null,
    val content: String? = null,
    val review: ReviewDto? = null,
    val education: EducationDto? = null,
    val imageUrls: List<String>? = null
)

data class PostSummaryResponse(
    val id: Long,
    val type: PostType,
    val title: String,
    val authorId: Long,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val createdAt: LocalDateTime?
)

data class PostResponse(
    val id: Long,
    val type: PostType,
    val title: String,
    val content: String,
    val authorId: Long,
    val images: List<String>,
    val review: ReviewDto? = null,
    val education: EducationDto? = null,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)

data class IdResponse(val id: Long)

data class CommunityHomeResponse(
    val recentReviews: List<PostSummaryResponse>,
    val newEducations: List<PostSummaryResponse>,
    val popularFreePosts: List<PostSummaryResponse>
)