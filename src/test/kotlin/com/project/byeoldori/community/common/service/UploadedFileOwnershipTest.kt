package com.project.byeoldori.community.common.service

import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.common.exception.UploadQuotaExceededException
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.common.domain.UploadedFile
import com.project.byeoldori.community.common.repository.UploadedFileRepository
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.dto.PostCreateRequest
import com.project.byeoldori.community.post.dto.PostUpdateRequest
import com.project.byeoldori.community.post.repository.*
import com.project.byeoldori.community.post.service.PostService
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
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDateTime

/**
 * 업로드 파일 소유권(IDOR) 회귀 테스트.
 * 남의 파일 URL 을 내 글에 붙였다 빼거나 글을 지워도, 그 파일이 스토리지에서 지워지면 안 된다.
 * 실제 JPA(H2 MySQL 모드) + StorageService 목으로 검증한다. 커밋 후 삭제는 등록된 동기화를 직접 실행해 확인한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UploadedFileOwnershipTest @Autowired constructor(
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
    private lateinit var uploadedFiles: UploadedFileService
    private lateinit var postService: PostService

    private val base = "http://localhost:8080/files"

    @BeforeEach
    fun setUp() {
        `when`(storage.isOwnUrl(anyString())).thenAnswer { (it.arguments[0] as String).startsWith("$base/") }
        uploadedFiles = UploadedFileService(
            uploadedRepo, imgRepo, calendarImageRepo, userRepo, storage,
            maxFilesPerHour = 3, maxBytesPerDay = 1_000,
        )
        postService = PostService(
            postRepo, reviewRepo, eduRepo, imgRepo, freePostRepo, siteRepo, likeRepo, ratingRepo, storage,
            ContentTargetService(targetRepo), mock(EducationProgramRepository::class.java), uploadedFiles,
        )
    }

    private fun user(n: Int) = em.persist(
        User(email = "own$n@test.com", passwordHash = "x", name = "u$n", phone = "010", nickname = "소유$n")
    )

    private fun uploaded(owner: User, name: String): String {
        val url = "$base/2026/10/20/$name.png"
        uploadedRepo.save(UploadedFile(url = url, uploaderId = owner.id, sizeBytes = 100))
        return url
    }

    private fun createPost(author: User, urls: List<String>): Long =
        postService.create(author, PostType.FREE, PostCreateRequest(title = "t", content = "c", imageUrls = urls))

    private fun runAfterCommit() =
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

    @Test
    fun `남의 이미지 URL 을 붙인 글을 지워도 원본 파일은 지워지지 않는다`() {
        val victim = user(1); val attacker = user(2)
        val victimUrl = uploaded(victim, "victim")
        createPost(victim, listOf(victimUrl))

        val attackPost = createPost(attacker, listOf(victimUrl))
        postService.delete(attackPost, attacker)
        runAfterCommit()

        verify(storage, never()).deleteImageByUrl(anyString())
        assertThat(uploadedRepo.findByUrl(victimUrl)).isNotNull
    }

    @Test
    fun `남의 이미지 URL 을 붙였다가 목록에서 빼도 원본 파일은 지워지지 않는다`() {
        val victim = user(1); val attacker = user(2)
        // 피해자의 글이 이미 지워져 아무도 참조하지 않는 상태여도 업로더가 아니면 지우지 않는다
        val victimUrl = uploaded(victim, "victim")

        val attackPost = createPost(attacker, listOf(victimUrl))
        postService.update(attackPost, PostUpdateRequest(imageUrls = emptyList()), attacker)
        runAfterCommit()

        verify(storage, never()).deleteImageByUrl(anyString())
    }

    @Test
    fun `내가 올린 파일은 글을 지우면 커밋 후에 지워지고 기록도 정리된다`() {
        val me = user(1)
        val mine = uploaded(me, "mine")
        val post = createPost(me, listOf(mine))

        postService.delete(post, me)
        verify(storage, never()).deleteImageByUrl(anyString()) // 커밋 전에는 지우지 않는다
        runAfterCommit()

        verify(storage).deleteImageByUrl(mine)
        assertThat(uploadedRepo.findByUrl(mine)).isNull()
    }

    @Test
    fun `내 파일이라도 다른 글이 아직 쓰고 있으면 지우지 않는다`() {
        val me = user(1)
        val shared = uploaded(me, "shared")
        val a = createPost(me, listOf(shared))
        createPost(me, listOf(shared))

        postService.update(a, PostUpdateRequest(imageUrls = emptyList()), me)
        runAfterCommit()

        verify(storage, never()).deleteImageByUrl(anyString())
    }

    @Test
    fun `프로필 이미지로 쓰이는 URL 과 기록 없는 URL 은 지우지 않는다`() {
        val me = user(1)
        val profileUrl = uploaded(me, "profile")
        me.profileImageUrl = profileUrl
        val unknown = "$base/2026/01/01/legacy.png" // V5 이전 업로드(기록 없음)
        val post = createPost(me, listOf(profileUrl, unknown))

        postService.delete(post, me)
        runAfterCommit()

        verify(storage, never()).deleteImageByUrl(anyString())
    }

    @Test
    fun `업로드 쿼터 - 시간당 개수와 24시간 용량을 넘으면 거부한다`() {
        val me = user(1); val other = user(2)
        val now = LocalDateTime.now()
        repeat(2) { uploadedRepo.save(UploadedFile(url = "$base/q$it.png", uploaderId = me.id, sizeBytes = 400)) }

        uploadedFiles.checkQuota(me.id, 100, now) // 2개·800B → 통과
        assertThatThrownBy { uploadedFiles.checkQuota(me.id, 300, now) } // 1,100B > 1,000B
            .isInstanceOf(UploadQuotaExceededException::class.java)

        uploadedRepo.save(UploadedFile(url = "$base/q2.png", uploaderId = me.id, sizeBytes = 1))
        assertThatThrownBy { uploadedFiles.checkQuota(me.id, 1, now) } // 3개 = 시간당 상한
            .isInstanceOf(UploadQuotaExceededException::class.java)

        uploadedFiles.checkQuota(other.id, 1_000, now) // 다른 사용자와는 무관
        uploadedFiles.checkQuota(me.id, 1, now.plusDays(2)) // 기간이 지나면 다시 허용
    }
}
