package com.project.byeoldori.community.post.service

import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.common.repository.UploadedFileRepository
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.community.common.service.UploadedFileService
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.dto.PostCreateRequest
import com.project.byeoldori.community.post.dto.PostUpdateRequest
import com.project.byeoldori.community.post.repository.*
import com.project.byeoldori.education.program.repository.EducationProgramRepository
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.star.repository.ContentTargetRepository
import com.project.byeoldori.star.service.ContentTargetService
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

/**
 * 게시글 입력 규칙: 수정 시 공백 제목·본문 거부, 이미지 URL 개수·길이 상한.
 * 실제 JPA(H2 MySQL 모드)로 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostInputValidationTest @Autowired constructor(
    private val em: TestEntityManager,
    private val uploadedRepo: UploadedFileRepository,
    private val postRepo: CommunityPostRepository,
    private val reviewRepo: ReviewPostRepository,
    private val eduRepo: EducationPostRepository,
    private val imgRepo: PostImageRepository,
    private val freePostRepo: FreePostRepository,
    private val siteRepo: ObservationSiteRepository,
    private val likeRepo: LikeRepository,
    private val ratingRepo: EducationRatingRepository,
    private val calendarImageRepo: CalendarImageRepository,
    private val userRepo: UserRepository,
    private val targetRepo: ContentTargetRepository,
) {
    private val storage: StorageService = mock(StorageService::class.java)
    private val postService by lazy {
        PostService(
            postRepo, reviewRepo, eduRepo, imgRepo, freePostRepo, siteRepo, likeRepo, ratingRepo, storage,
            ContentTargetService(targetRepo), mock(EducationProgramRepository::class.java),
            UploadedFileService(uploadedRepo, imgRepo, calendarImageRepo, userRepo, storage, 120, 1_000_000),
        )
    }

    private fun author() = em.persist(
        User(email = "in@test.com", passwordHash = "x", name = "u", phone = "010", nickname = "입력검사")
    )

    @Test
    fun `수정 때 공백뿐인 제목·본문은 거부하고 기존 값을 지킨다`() {
        val me = author()
        val id = postService.create(me, PostType.FREE, PostCreateRequest(title = "원래 제목", content = "원래 본문"))

        assertThatThrownBy { postService.update(id, PostUpdateRequest(title = "  "), me) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThatThrownBy { postService.update(id, PostUpdateRequest(content = ""), me) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThat(postRepo.findById(id).get().title).isEqualTo("원래 제목")

        // null 은 "바꾸지 않음"
        postService.update(id, PostUpdateRequest(title = "새 제목"), me)
        assertThat(postRepo.findById(id).get().title).isEqualTo("새 제목")
    }

    @Test
    fun `이미지 URL 이 컬럼 길이(512)를 넘거나 개수 상한을 넘으면 거부한다`() {
        val me = author()
        val tooLong = "https://x/" + "a".repeat(510)
        assertThatThrownBy {
            postService.create(me, PostType.FREE, PostCreateRequest(title = "t", content = "c", imageUrls = listOf(tooLong)))
        }.isInstanceOf(InvalidInputException::class.java)

        val id = postService.create(me, PostType.FREE, PostCreateRequest(title = "t", content = "c"))
        assertThatThrownBy {
            postService.update(id, PostUpdateRequest(imageUrls = (1..21).map { "https://x/$it.png" }), me)
        }.isInstanceOf(InvalidInputException::class.java)
    }
}
