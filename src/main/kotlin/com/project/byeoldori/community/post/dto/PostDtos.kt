package com.project.byeoldori.community.post.dto

import com.project.byeoldori.community.common.domain.*
import com.project.byeoldori.community.post.domain.EducationPost
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalDateTime

data class ReviewDto(
    val location: String? = null,
    val observationSiteId: Long? = null,
    val target: String? = null,
    val equipment: String? = null,
    val observationDate: LocalDate? = null,
    @field:Min(1) @field:Max(5)
    val score: Int? = null // 1~5점
)

data class EducationRequestDto(
    val difficulty: EducationDifficulty? = null,
    val target: String? = null,
    val tags: String? = null,
    val status: EducationStatus? = null // null → DRAFT
)

data class EducationResponseDto(
    val difficulty: EducationDifficulty? = null,
    val target: String? = null,
    val tags: String? = null,
    val status: EducationStatus? = null,
    val averageScore: Double = 0.0
) {
    companion object {
        fun from(educationPost: EducationPost) = EducationResponseDto(
            difficulty = educationPost.difficulty,
            target = educationPost.target,
            tags = educationPost.tags,
            status = educationPost.status,
            averageScore = educationPost.averageScore
        )
    }
}

data class PostCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 120) val title: String,

    @field:NotBlank(message = "본문은 필수입니다.")
    val content: String,
    val review: ReviewDto? = null,
    val education: EducationRequestDto? = null,
    val imageUrls: List<String>? = emptyList()
)

data class PostUpdateRequest(
    @field:Size(max = 120) val title: String? = null,
    val content: String? = null,
    val review: ReviewDto? = null,
    val education: EducationRequestDto? = null,
    val imageUrls: List<String>? = null
)

data class RateEducationRequest(
    @field:Min(value = 1, message = "평점은 1 이상이어야 합니다.")
    @field:Max(value = 5, message = "평점은 5 이하여야 합니다.")
    val score: Int
)

data class PostSummaryResponse( // 홈 화면에서 조회
    val id: Long,
    val type: PostType,
    val title: String,
    val authorId: Long,
    val authorNickname: String?,
    val observationSiteId: Long? = null,
    val contentSummary: String? = null,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val createdAt: LocalDateTime?,
    val liked: Boolean = false,
    val score: Double? = 0.0,
    val thumbnailUrl: String? = null
)

data class PostResponse( // 상세 조회
    val id: Long,
    val type: PostType,
    val title: String,
    val content: String,
    val authorId: Long,
    val images: List<String>,
    val review: ReviewDto? = null,
    val education: EducationResponseDto? = null,
    val viewCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val liked: Boolean = false
)

data class IdResponse(val id: Long)