package com.project.byeoldori.community.post.dto

import com.project.byeoldori.community.common.domain.*
import com.project.byeoldori.community.post.domain.EducationPost
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalDateTime

// 문자열 길이 제한은 review_post / education_post 컬럼 길이와 맞춘다(넘으면 DB 저장 단계에서 500).
data class ReviewDto(
    @field:Size(max = 255) val location: String? = null,
    val observationSiteId: Long? = null,
    val targets: List<String>? = null,
    @field:Size(max = 255) val equipment: String? = null,
    val observationDate: LocalDate? = null,
    @field:Min(1) @field:Max(5)
    val score: Int? = null // 1~5점
)

data class EducationRequestDto(
    val difficulty: EducationDifficulty? = null,
    val targets: List<String>? = null,
    @field:Size(max = 255) val tags: String? = null,
    val status: EducationStatus? = null, // null → DRAFT
    @field:Size(max = 1024) val contentUrl: String? = null, // 생성시 입력하지 않음
    @field:Size(max = 64) val programId: String? = null // 연결할 인터랙티브 프로그램(MongoDB) id
)

data class EducationResponseDto(
    val difficulty: EducationDifficulty? = null,
    val targets: List<String> = emptyList(),
    val tags: String? = null,
    val status: EducationStatus? = null,
    val averageScore: Double = 0.0,
    val contentUrl: String? = null,
    val programId: String? = null
) {
    companion object {
        fun from(educationPost: EducationPost,targets: List<String>) = EducationResponseDto(
            difficulty = educationPost.difficulty,
            targets = targets,
            tags = educationPost.tags,
            status = educationPost.status,
            averageScore = educationPost.averageScore,
            contentUrl = educationPost.contentUrl,
            programId = educationPost.programId
        )
    }
}

data class PostCreateRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 120) val title: String,

    @field:NotBlank(message = "본문은 필수입니다.")
    val content: String,
    // @Valid 가 없으면 중첩 DTO 의 제약(별점 1~5, 길이)이 검사되지 않는다.
    @field:Valid val review: ReviewDto? = null,
    @field:Valid val education: EducationRequestDto? = null,
    val imageUrls: List<String>? = emptyList()
)

data class PostUpdateRequest(
    @field:Size(max = 120) val title: String? = null,
    val content: String? = null,
    @field:Valid val review: ReviewDto? = null,
    @field:Valid val education: EducationRequestDto? = null,
    val imageUrls: List<String>? = null
)

data class EvaluateEducationRequest(
    @field:Min(value = 1, message = "평점은 1점 이상이어야 합니다.")
    @field:Max(value = 5, message = "평점은 5점 이하여야 합니다.")
    val score: Int,
    val pros: String? = null,
    val cons: String? = null
)

data class PostSummaryResponse( // 홈 화면에서 조회
    val id: Long,
    val type: PostType,
    val title: String,
    val authorId: Long,
    val authorNickname: String?,
    val authorProfileImageUrl: String? = null,
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
    val authorNickname: String?,
    val authorProfileImageUrl: String? = null,
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