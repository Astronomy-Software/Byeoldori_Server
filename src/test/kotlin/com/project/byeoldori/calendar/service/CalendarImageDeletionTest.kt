package com.project.byeoldori.calendar.service

import com.project.byeoldori.calendar.dto.UpdateEventRequest
import com.project.byeoldori.calendar.entity.CalendarImage
import com.project.byeoldori.calendar.entity.ObservationEvent
import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.calendar.repository.ObservationEventRepository
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.community.common.domain.UploadedFile
import com.project.byeoldori.community.common.repository.UploadedFileRepository
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.community.common.service.UploadedFileService
import com.project.byeoldori.community.post.repository.PostImageRepository
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
 * 관측 일정 사진 삭제: 검증 실패(롤백) 때 파일이 먼저 지워지지 않는지, 커밋 후에만·본인 파일만 지우는지 확인한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CalendarImageDeletionTest @Autowired constructor(
    private val em: TestEntityManager,
    private val events: ObservationEventRepository,
    private val photos: CalendarImageRepository,
    private val siteRepo: ObservationSiteRepository,
    private val targetRepo: ContentTargetRepository,
    private val uploadedRepo: UploadedFileRepository,
    private val imgRepo: PostImageRepository,
    private val userRepo: UserRepository,
) {
    private val storage: StorageService = mock(StorageService::class.java)
    private lateinit var service: CalendarService

    @BeforeEach
    fun setUp() {
        val uploadedFiles = UploadedFileService(
            uploadedRepo, imgRepo, photos, userRepo, storage,
            maxFilesPerHour = 100, maxBytesPerDay = 1_000_000,
        )
        service = CalendarService(events, photos, uploadedFiles, siteRepo, ContentTargetService(targetRepo))
    }

    private fun user(n: Int) = em.persist(
        User(email = "cal$n@test.com", passwordHash = "x", name = "u$n", phone = "010", nickname = "달력$n")
    )

    private fun ownedUrl(owner: User, name: String): String {
        val url = "http://localhost:8080/files/2026/10/20/$name.png"
        uploadedRepo.save(UploadedFile(url = url, uploaderId = owner.id, sizeBytes = 1))
        return url
    }

    private fun eventWithPhotos(owner: User, urls: List<String>): Pair<ObservationEvent, List<CalendarImage>> {
        val e = em.persist(ObservationEvent(userId = owner.id, title = "관측", startAt = LocalDateTime.of(2026, 10, 20, 21, 0)))
        val imgs = urls.map { em.persist(CalendarImage(event = e, url = it)) }
        em.flush()
        return e to imgs
    }

    private fun runAfterCommit() =
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

    @Test
    fun `사진 10장에서 1장 삭제와 2장 추가를 함께 보내면 400 이고 파일은 지우지 않는다`() {
        val me = user(1)
        val (e, imgs) = eventWithPhotos(me, (0 until 10).map { ownedUrl(me, "p$it") })

        assertThatThrownBy {
            service.update(me, e.id!!, UpdateEventRequest(
                removeImageIds = listOf(imgs[0].id!!),
                addImageUrls = listOf(ownedUrl(me, "n1"), ownedUrl(me, "n2")),
            ))
        }.isInstanceOf(InvalidInputException::class.java)

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty()
        verify(storage, never()).deleteImageByUrl(anyString())
        assertThat(photos.countByEventId(e.id!!)).isEqualTo(10)
    }

    @Test
    fun `사진 삭제는 커밋 후에 실행되고, 같은 요청에서 다시 붙인 URL 은 지우지 않는다`() {
        val me = user(1)
        val keep = ownedUrl(me, "keep"); val gone = ownedUrl(me, "gone")
        val (e, imgs) = eventWithPhotos(me, listOf(keep, gone))

        service.update(me, e.id!!, UpdateEventRequest(
            removeImageIds = imgs.map { it.id!! },
            addImageUrls = listOf(keep),
        ))
        verify(storage, never()).deleteImageByUrl(anyString())
        runAfterCommit()

        verify(storage).deleteImageByUrl(gone)
        verify(storage, never()).deleteImageByUrl(keep)
    }

    @Test
    fun `일정을 지우면 내가 올린 사진만 커밋 후 지운다`() {
        val me = user(1); val other = user(2)
        val mine = ownedUrl(me, "mine"); val others = ownedUrl(other, "others")
        val (e, _) = eventWithPhotos(me, listOf(mine, others))

        service.delete(me, e.id!!)
        verify(storage, never()).deleteImageByUrl(anyString())
        runAfterCommit()

        verify(storage).deleteImageByUrl(mine)
        verify(storage, never()).deleteImageByUrl(others)
    }
}
