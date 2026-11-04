package com.project.byeoldori.community.post.service

import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.common.domain.UploadedFile
import com.project.byeoldori.community.common.repository.UploadedFileRepository
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.community.common.service.UploadedFileService
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.dto.PostCreateRequest
import com.project.byeoldori.community.post.domain.PostImage
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionSynchronizationManager

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

    private val base = "http://localhost:8080/files"

    @BeforeEach
    fun setUp() {
        `when`(storage.isOwnUrl(anyString())).thenAnswer { (it.arguments[0] as String).startsWith("$base/") }
    }

    private fun author() = em.persist(
        User(email = "in@test.com", passwordHash = "x", name = "u", phone = "010", nickname = "입력검사")
    )

    private fun user(n: Int, admin: Boolean = false) = em.persist(
        User(email = "in$n@test.com", passwordHash = "x", name = "u$n", phone = "010", nickname = "사용자$n")
            .apply { if (admin) roles = mutableSetOf("USER", "ADMIN") }
    )

    private fun runAfterCommit() =
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

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

    @Test
    fun `새로 붙이는 이미지는 우리 스토리지 URL 만 받는다`() {
        val me = author()
        assertThatThrownBy {
            postService.create(me, PostType.FREE, PostCreateRequest(title = "t", content = "c", imageUrls = listOf("https://attacker.example/x.png")))
        }.isInstanceOf(InvalidInputException::class.java)

        val id = postService.create(me, PostType.FREE, PostCreateRequest(title = "t", content = "c", imageUrls = listOf("$base/2026/10/20/a.png")))
        assertThatThrownBy {
            postService.update(id, PostUpdateRequest(imageUrls = listOf("$base/2026/10/20/a.png", "https://attacker.example/x.png")), me)
        }.isInstanceOf(InvalidInputException::class.java)
    }

    @Test
    fun `이미 저장된 예전 도메인 이미지는 수정 때 그대로 둘 수 있다`() {
        val me = author()
        val id = postService.create(me, PostType.FREE, PostCreateRequest(title = "t", content = "c"))
        val legacy = "https://old-domain.example/files/2025/01/01/legacy.png"
        imgRepo.save(PostImage(post = postRepo.findById(id).get(), url = legacy, sortOrder = 0))

        postService.update(id, PostUpdateRequest(title = "새 제목", imageUrls = listOf(legacy, "$base/2026/10/20/b.png")), me)

        assertThat(imgRepo.findAllByPostIdOrderBySortOrderAsc(id).map { it.url })
            .containsExactly(legacy, "$base/2026/10/20/b.png")
    }

    @Test
    fun `관리자는 남의 글을 지울 수 있고 파일 정리는 작성자 기준이다`() {
        val writer = user(1); val stranger = user(2); val admin = user(3, admin = true)
        val url = "$base/2026/10/20/w.png"
        uploadedRepo.save(UploadedFile(url = url, uploaderId = writer.id, sizeBytes = 100))
        val id = postService.create(writer, PostType.FREE, PostCreateRequest(title = "스팸", content = "c", imageUrls = listOf(url)))

        assertThatThrownBy { postService.delete(id, stranger) }.isInstanceOf(ForbiddenException::class.java)
        em.flush(); em.clear() // 실제로는 작성과 삭제가 다른 트랜잭션이다

        postService.delete(id, admin)
        em.flush(); em.clear()
        runAfterCommit()

        assertThat(postRepo.findById(id)).isEmpty
        verify(storage).deleteImageByUrl(url)
        assertThat(uploadedRepo.findByUrl(url)).isNull()
    }

    @Test
    fun `작성자 본인의 상세 조회는 조회수를 올리지 않는다`() {
        val writer = user(4); val reader = user(5)
        val id = postService.create(writer, PostType.FREE, PostCreateRequest(title = "t", content = "c"))

        assertThat(postService.detail(id, writer).viewCount).isZero()
        assertThat(postService.detail(id, reader).viewCount).isEqualTo(1)
        em.flush(); em.clear()
        assertThat(postRepo.findById(id).get().viewCount).isEqualTo(1)
    }
}
