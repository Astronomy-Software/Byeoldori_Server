package com.project.byeoldori.user.service

import com.project.byeoldori.calendar.entity.CalendarImage
import com.project.byeoldori.calendar.entity.ObservationEvent
import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.calendar.repository.ObservationEventRepository
import com.project.byeoldori.community.common.domain.UploadedFile
import com.project.byeoldori.community.common.repository.UploadedFileRepository
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.community.common.service.UploadedFileService
import com.project.byeoldori.community.post.repository.PostImageRepository
import com.project.byeoldori.education.program.repository.QuizAttemptRepository
import com.project.byeoldori.notification.entity.Notification
import com.project.byeoldori.notification.entity.NotificationType
import com.project.byeoldori.notification.repository.NotificationRepository
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import com.project.byeoldori.star.entity.ContentTarget
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.star.repository.ContentTargetRepository
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.io.File
import java.time.LocalDateTime

/**
 * 회원 탈퇴 시 개인 데이터 파기(AccountDataPurgeService)와, 이미 탈퇴한 계정용 일회성 정리(V7 마이그레이션)를
 * 실제 JPA(H2 MySQL 모드)로 검증한다. 남의 데이터·공개 데이터는 그대로 남아야 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountDataPurgeTest @Autowired constructor(
    private val em: TestEntityManager,
    private val events: ObservationEventRepository,
    private val photos: CalendarImageRepository,
    private val targetRepo: ContentTargetRepository,
    private val uploadedRepo: UploadedFileRepository,
    private val imgRepo: PostImageRepository,
    private val userRepo: UserRepository,
    private val savedSites: UserSavedSiteRepository,
    private val notifications: NotificationRepository,
) {
    private val storage: StorageService = mock(StorageService::class.java)
    private val quizAttempts: QuizAttemptRepository = mock(QuizAttemptRepository::class.java)
    private lateinit var service: AccountDataPurgeService

    @BeforeEach
    fun setUp() {
        val uploadedFiles = UploadedFileService(
            uploadedRepo, imgRepo, photos, userRepo, storage,
            maxFilesPerHour = 100, maxBytesPerDay = 1_000_000,
        )
        service = AccountDataPurgeService(events, photos, targetRepo, uploadedFiles, savedSites, notifications, quizAttempts)
    }

    private fun user(n: Int, deleted: Boolean = false) = em.persist(
        User(email = "purge$n@test.com", passwordHash = "x", name = "u$n", phone = "010", nickname = "파기$n")
            .apply { if (deleted) deletedAt = LocalDateTime.now() }
    )

    /** 일정 1건 + 사진 1장(업로더 기록 포함) + 대상 천체 태그 + 즐겨찾기 좌표 + 알림을 만든다. */
    private fun seed(owner: User, tag: String): String {
        val e = em.persist(
            ObservationEvent(
                userId = owner.id, title = "집 앞 관측", startAt = LocalDateTime.of(2026, 10, 20, 21, 0),
                lat = 37.5, lon = 127.0, placeName = "우리 집", memo = "비밀 메모"
            )
        )
        val url = "http://localhost:8080/files/2026/10/20/$tag.png"
        uploadedRepo.save(UploadedFile(url = url, uploaderId = owner.id, sizeBytes = 1))
        em.persist(CalendarImage(event = e, url = url))
        em.persist(ContentTarget(contentType = ContentType.EVENT, contentId = e.id!!, starObjectName = "Orion"))
        em.persist(UserSavedSite(user = owner, customName = "집", customLatitude = 37.5, customLongitude = 127.0))
        em.persist(Notification(user = owner, type = NotificationType.SYSTEM, title = "알림", body = "본문"))
        em.flush()
        return url
    }

    private fun count(table: String, userColumn: String, userId: Long): Long =
        (em.entityManager.createNativeQuery("select count(*) from $table where $userColumn = :id")
            .setParameter("id", userId).singleResult as Number).toLong()

    private fun eventTagCount(): Long =
        (em.entityManager.createNativeQuery("select count(*) from content_target where content_type = 'EVENT'")
            .singleResult as Number).toLong()

    private fun runAfterCommit() =
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

    @Test
    fun `탈퇴하면 본인 일정·사진·태그·즐겨찾기·알림을 지우고 사진 파일과 퀴즈 기록은 커밋 후 지운다`() {
        val me = user(1)
        val other = user(2)
        val myUrl = seed(me, "mine")
        val otherUrl = seed(other, "theirs")

        service.purgePrivateData(me.id)
        em.flush(); em.clear()

        assertThat(events.findAllByUserId(me.id)).isEmpty()
        assertThat(photos.existsByUrl(myUrl)).isFalse()
        assertThat(count("user_saved_sites", "user_id", me.id)).isZero()
        assertThat(count("notifications", "user_id", me.id)).isZero()
        assertThat(uploadedRepo.findByUrl(myUrl)).isNull()
        assertThat(eventTagCount()).isEqualTo(1) // 남의 일정 태그만 남는다

        // 남의 데이터는 그대로
        assertThat(events.findAllByUserId(other.id)).hasSize(1)
        assertThat(photos.existsByUrl(otherUrl)).isTrue()
        assertThat(count("user_saved_sites", "user_id", other.id)).isEqualTo(1)
        assertThat(count("notifications", "user_id", other.id)).isEqualTo(1)

        // 파일·Mongo 기록은 커밋 전에는 지우지 않는다(롤백되면 계정이 살아 있으므로)
        verify(storage, never()).deleteImageByUrl(myUrl)
        verify(quizAttempts, never()).deleteByUserId(me.id)
        runAfterCommit()
        verify(storage).deleteImageByUrl(myUrl)
        verify(storage, never()).deleteImageByUrl(otherUrl)
        verify(quizAttempts).deleteByUserId(me.id)
    }

    @Test
    fun `일정이 없는 사용자도 즐겨찾기·알림·퀴즈 기록은 지운다`() {
        val me = user(3)
        em.persist(UserSavedSite(user = me, customName = "집", customLatitude = 1.0, customLongitude = 2.0))
        em.persist(Notification(user = me, type = NotificationType.SYSTEM, title = "알림", body = "본문"))
        em.flush()

        service.purgePrivateData(me.id)
        em.flush(); em.clear()

        assertThat(count("user_saved_sites", "user_id", me.id)).isZero()
        assertThat(count("notifications", "user_id", me.id)).isZero()
        runAfterCommit()
        verify(quizAttempts).deleteByUserId(me.id)
    }

    @Test
    fun `V7 마이그레이션은 이미 탈퇴한 계정의 개인 데이터만 지우고 다시 실행해도 같다`() {
        val gone = user(4, deleted = true)
        val alive = user(5)
        val goneUrl = seed(gone, "gone")
        seed(alive, "alive")

        val sql = File("src/main/resources/db/migration/V7__purge_deleted_users_private_data.sql").readLines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertThat(sql).hasSize(5)
        repeat(2) { sql.forEach { em.entityManager.createNativeQuery(it).executeUpdate() } }
        em.clear()

        assertThat(count("observation_event", "user_id", gone.id)).isZero()
        assertThat(photos.existsByUrl(goneUrl)).isFalse()
        assertThat(count("user_saved_sites", "user_id", gone.id)).isZero()
        assertThat(count("notifications", "user_id", gone.id)).isZero()
        assertThat(eventTagCount()).isEqualTo(1)
        // 업로더 기록은 파일 정리 근거로 남긴다
        assertThat(uploadedRepo.findByUrl(goneUrl)).isNotNull()

        assertThat(count("observation_event", "user_id", alive.id)).isEqualTo(1)
        assertThat(count("user_saved_sites", "user_id", alive.id)).isEqualTo(1)
        assertThat(count("notifications", "user_id", alive.id)).isEqualTo(1)
    }
}
