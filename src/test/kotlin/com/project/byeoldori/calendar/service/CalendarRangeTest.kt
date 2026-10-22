package com.project.byeoldori.calendar.service

import com.project.byeoldori.calendar.entity.ObservationEvent
import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.calendar.repository.ObservationEventRepository
import com.project.byeoldori.common.exception.InvalidInputException
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
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 일별·월별 조회 구간이 [시작, 끝) 반열린 구간인지, 범위 밖 year/month 가 500 이 아니라 400 인지 확인한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CalendarRangeTest @Autowired constructor(
    private val em: TestEntityManager,
    private val events: ObservationEventRepository,
    private val photos: CalendarImageRepository,
    private val siteRepo: ObservationSiteRepository,
    private val targetRepo: ContentTargetRepository,
    private val uploadedRepo: UploadedFileRepository,
    private val imgRepo: PostImageRepository,
    private val userRepo: UserRepository,
) {
    private lateinit var service: CalendarService

    @BeforeEach
    fun setUp() {
        val uploadedFiles = UploadedFileService(
            uploadedRepo, imgRepo, photos, userRepo, mock(StorageService::class.java),
            maxFilesPerHour = 100, maxBytesPerDay = 1_000_000,
        )
        service = CalendarService(events, photos, uploadedFiles, siteRepo, ContentTargetService(targetRepo))
    }

    private fun user(n: Int) = em.persist(
        User(email = "range$n@test.com", passwordHash = "x", name = "u$n", phone = "010", nickname = "구간$n")
    )

    private fun event(owner: User, title: String, startAt: LocalDateTime) =
        em.persist(ObservationEvent(userId = owner.id, title = title, startAt = startAt))

    @Test
    fun `자정에 시작하는 일정은 당일 목록에만 나온다`() {
        val me = user(1)
        event(me, "전날 밤", LocalDateTime.of(2026, 10, 20, 23, 0))
        event(me, "자정", LocalDateTime.of(2026, 10, 21, 0, 0))
        em.flush(); em.clear()

        assertThat(service.listByDate(me, LocalDate.of(2026, 10, 20)).map { it.title }).containsExactly("전날 밤")
        assertThat(service.listByDate(me, LocalDate.of(2026, 10, 21)).map { it.title }).containsExactly("자정")
    }

    @Test
    fun `다음 달 1일 00시 일정은 이전 달 요약에 섞이지 않는다`() {
        val me = user(2)
        event(me, "10월 1일 자정", LocalDateTime.of(2026, 10, 1, 0, 0))
        event(me, "10월 말", LocalDateTime.of(2026, 10, 31, 22, 0))
        event(me, "11월 1일 자정", LocalDateTime.of(2026, 11, 1, 0, 0))
        em.flush(); em.clear()

        assertThat(service.monthlySummary(me, 2026, 10).map { it.date })
            .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertThat(service.monthlySummary(me, 2026, 11).map { it.date })
            .containsExactly(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `범위 밖 month·year 는 InvalidInputException(400)`() {
        val me = user(3)
        for ((y, m) in listOf(2026 to 0, 2026 to 13, 1899 to 1, 2101 to 12)) {
            assertThatThrownBy { service.monthlySummary(me, y, m) }
                .`as`("year=%d month=%d", y, m)
                .isInstanceOf(InvalidInputException::class.java)
        }
    }
}
