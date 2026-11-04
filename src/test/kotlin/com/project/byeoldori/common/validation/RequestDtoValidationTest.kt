package com.project.byeoldori.common.validation

import com.project.byeoldori.calendar.controller.CalendarController
import com.project.byeoldori.calendar.dto.CreateEventRequest
import com.project.byeoldori.calendar.dto.UpdateEventRequest
import com.project.byeoldori.community.post.dto.EducationRequestDto
import com.project.byeoldori.community.post.dto.PostCreateRequest
import com.project.byeoldori.community.post.dto.PostUpdateRequest
import com.project.byeoldori.community.post.dto.ReviewDto
import com.project.byeoldori.star.service.CONTENT_TARGET_MAX_COUNT
import jakarta.validation.Valid
import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 요청 DTO 검증: 컬럼 길이를 넘는 값과 중첩 DTO 제약이 400 단계(Bean Validation)에서 걸리는지.
 * 예전에는 길이 초과가 DB 저장 단계 500, 중첩 ReviewDto 의 별점 1~5 제약은 아예 검사되지 않았다.
 */
class RequestDtoValidationTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun violations(target: Any) = validator.validate(target).map { it.propertyPath.toString() }

    @Test
    fun `게시글 생성·수정 - 중첩 후기 별점 범위를 검사한다`() {
        val create = PostCreateRequest(title = "t", content = "c", review = ReviewDto(score = 999))
        assertThat(violations(create)).containsExactly("review.score")

        val update = PostUpdateRequest(review = ReviewDto(score = -5))
        assertThat(violations(update)).containsExactly("review.score")

        assertThat(violations(PostCreateRequest(title = "t", content = "c", review = ReviewDto(score = 5)))).isEmpty()
    }

    @Test
    fun `게시글 - 후기·교육 문자열 길이를 컬럼 길이에 맞춰 제한한다`() {
        val req = PostCreateRequest(
            title = "t", content = "c",
            review = ReviewDto(location = "가".repeat(256), equipment = "나".repeat(256)),
            education = EducationRequestDto(tags = "#".repeat(256), programId = "p".repeat(65)),
        )
        assertThat(violations(req)).containsExactlyInAnyOrder(
            "review.location", "review.equipment", "education.tags", "education.programId"
        )
        val ok = PostCreateRequest(
            title = "t", content = "c",
            review = ReviewDto(location = "가".repeat(255)), education = EducationRequestDto(programId = "p".repeat(64)),
        )
        assertThat(violations(ok)).isEmpty()
    }

    @Test
    fun `게시글 - 본문 길이와 이미지 개수 상한을 넘으면 거부한다`() {
        val tooManyImages = (1..21).map { "https://x/$it.png" }
        assertThat(violations(PostCreateRequest(title = "t", content = "c".repeat(1_000_001), imageUrls = tooManyImages)))
            .containsExactlyInAnyOrder("content", "imageUrls")
        assertThat(violations(PostUpdateRequest(content = "c".repeat(1_000_001), imageUrls = tooManyImages)))
            .containsExactlyInAnyOrder("content", "imageUrls")
        assertThat(violations(PostCreateRequest(title = "t", content = "c", imageUrls = tooManyImages.take(20)))).isEmpty()
    }

    @Test
    fun `캘린더 - 제목 120자·장소 255자를 넘으면 거부한다`() {
        val start = LocalDateTime.of(2026, 10, 20, 21, 0)
        assertThat(violations(CreateEventRequest(title = "가".repeat(121), startAt = start, placeName = "나".repeat(256))))
            .containsExactlyInAnyOrder("title", "placeName")
        assertThat(violations(CreateEventRequest(title = "가".repeat(120), startAt = start))).isEmpty()
        assertThat(violations(UpdateEventRequest(title = "가".repeat(121), placeName = "나".repeat(256))))
            .containsExactlyInAnyOrder("title", "placeName")
    }

    @Test
    fun `게시글·캘린더 - 대상 천체 개수 상한을 넘으면 거부한다`() {
        val tooMany = (1..CONTENT_TARGET_MAX_COUNT + 1).map { "star-$it" }
        val ok = tooMany.take(CONTENT_TARGET_MAX_COUNT)
        val start = LocalDateTime.of(2026, 10, 20, 21, 0)

        assertThat(violations(PostCreateRequest(
            title = "t", content = "c",
            review = ReviewDto(targets = tooMany), education = EducationRequestDto(targets = tooMany),
        ))).containsExactlyInAnyOrder("review.targets", "education.targets")
        assertThat(violations(PostUpdateRequest(review = ReviewDto(targets = tooMany)))).containsExactly("review.targets")
        assertThat(violations(CreateEventRequest(title = "t", startAt = start, targets = tooMany))).containsExactly("targets")
        assertThat(violations(UpdateEventRequest(targets = tooMany))).containsExactly("targets")

        assertThat(violations(PostCreateRequest(title = "t", content = "c", review = ReviewDto(targets = ok)))).isEmpty()
        assertThat(violations(CreateEventRequest(title = "t", startAt = start, targets = ok))).isEmpty()
    }

    @Test
    fun `캘린더 수정 API 는 요청 본문을 검증한다(@Valid)`() {
        val update = CalendarController::class.java.methods.first { it.name == "update" }
        val bodyParam = update.parameters.first { it.type == UpdateEventRequest::class.java }
        assertThat(bodyParam.isAnnotationPresent(Valid::class.java)).isTrue()
    }
}
