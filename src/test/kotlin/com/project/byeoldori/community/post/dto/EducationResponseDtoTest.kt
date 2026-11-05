package com.project.byeoldori.community.post.dto

import com.project.byeoldori.community.common.domain.EducationDifficulty
import com.project.byeoldori.community.common.domain.EducationStatus
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.domain.EducationPost
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EducationResponseDtoTest {

    private val author = User(id = 1, email = "a@test.com", passwordHash = "x", name = "a", phone = "010")
    private val post = CommunityPost(id = 10, author = author, type = PostType.EDUCATION, title = "교육글", content = "{}")

    @Test
    fun `교육 정보와 대상 천체를 응답으로 옮긴다`() {
        val edu = EducationPost(
            post = post, difficulty = EducationDifficulty.INTERMEDIATE, tags = "별자리,관측기초", status = EducationStatus.PUBLISHED,
            contentUrl = "https://api/files/json/x.json", programId = "abc123", averageScore = 4.25, ratingCount = 8,
        )

        val res = EducationResponseDto.from(edu, listOf("M31", "M42"))

        assertThat(res.difficulty).isEqualTo(EducationDifficulty.INTERMEDIATE)
        assertThat(res.targets).containsExactly("M31", "M42")
        assertThat(res.tags).isEqualTo("별자리,관측기초")
        assertThat(res.status).isEqualTo(EducationStatus.PUBLISHED)
        assertThat(res.averageScore).isEqualTo(4.25)
        assertThat(res.contentUrl).isEqualTo("https://api/files/json/x.json")
        assertThat(res.programId).isEqualTo("abc123")
    }

    @Test
    fun `새 교육글 기본값 - DRAFT, 평점 0, 연결 프로그램 없음`() {
        val res = EducationResponseDto.from(EducationPost(post = post), emptyList())
        assertThat(res.status).isEqualTo(EducationStatus.DRAFT)
        assertThat(res.difficulty).isNull()
        assertThat(res.averageScore).isEqualTo(0.0)
        assertThat(res.programId).isNull()
        assertThat(res.contentUrl).isNull()
        assertThat(res.targets).isEmpty()
    }

    @Test
    fun `게시글 요청 DTO 기본값과 상한 상수`() {
        val create = PostCreateRequest(title = "t", content = "c")
        assertThat(create.imageUrls).isEmpty()
        assertThat(create.review).isNull()
        assertThat(create.education).isNull()

        val update = PostUpdateRequest()
        assertThat(update.imageUrls).isNull() // 수정에서 null 은 "그대로 두기"
        assertThat(update.title).isNull()

        assertThat(POST_IMAGE_MAX_COUNT).isEqualTo(20)
        assertThat(POST_CONTENT_MAX_LENGTH).isEqualTo(1_000_000)
    }
}
