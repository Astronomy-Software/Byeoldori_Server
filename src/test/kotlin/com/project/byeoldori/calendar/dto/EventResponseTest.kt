package com.project.byeoldori.calendar.dto

import com.project.byeoldori.calendar.entity.CalendarImage
import com.project.byeoldori.calendar.entity.EventStatus
import com.project.byeoldori.calendar.entity.ObservationEvent
import com.project.byeoldori.observationsites.entity.ObservationSite
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class EventResponseTest {

    private val site = ObservationSite(id = 3, name = "별마로 천문대", latitude = 37.19, longitude = 128.48)

    private fun event(site: ObservationSite? = this.site, id: Long? = 7) = ObservationEvent(
        id = id, userId = 1, title = "페르세우스 유성우", startAt = LocalDateTime.of(2026, 8, 12, 23, 0),
        endAt = LocalDateTime.of(2026, 8, 13, 3, 0), lat = 37.2, lon = 128.5, placeName = "영월",
        observationSite = site, status = EventStatus.COMPLETED, memo = "맑았다",
    )

    @Test
    fun `엔티티·이미지·대상 천체를 응답으로 합친다`() {
        val e = event()
        val images = listOf(
            CalendarImage(id = 11, event = e, url = "https://img/1.jpeg", contentType = "image/jpeg"),
            CalendarImage(id = 12, event = e, url = "https://img/2.png", contentType = null),
        )

        val res = EventResponse.from(e, images, listOf("M45", "목성"))

        assertThat(res.id).isEqualTo(7)
        assertThat(res.title).isEqualTo("페르세우스 유성우")
        assertThat(res.startAt).isEqualTo(LocalDateTime.of(2026, 8, 12, 23, 0))
        assertThat(res.endAt).isEqualTo(LocalDateTime.of(2026, 8, 13, 3, 0))
        assertThat(res.targets).containsExactly("M45", "목성")
        assertThat(res.observationSiteId).isEqualTo(3)
        assertThat(res.observationSiteName).isEqualTo("별마로 천문대")
        assertThat(res.lat).isEqualTo(37.2)
        assertThat(res.lon).isEqualTo(128.5)
        assertThat(res.placeName).isEqualTo("영월")
        assertThat(res.status).isEqualTo(EventStatus.COMPLETED)
        assertThat(res.memo).isEqualTo("맑았다")
        assertThat(res.photos).containsExactly(
            PhotoResponse(11, "https://img/1.jpeg", "image/jpeg"),
            PhotoResponse(12, "https://img/2.png", null),
        )
        assertThat(res.createdAt).isEqualTo(e.createdAt)
        assertThat(res.updatedAt).isEqualTo(e.updatedAt)
    }

    @Test
    fun `관측지가 없으면 id·이름이 null`() {
        val res = EventResponse.from(event(site = null), emptyList(), emptyList())
        assertThat(res.observationSiteId).isNull()
        assertThat(res.observationSiteName).isNull()
        assertThat(res.photos).isEmpty()
        assertThat(res.targets).isEmpty()
    }

    @Test
    fun `저장 전(id null) 엔티티는 변환할 수 없다`() {
        assertThatThrownBy { EventResponse.from(event(id = null), emptyList(), emptyList()) }
            .isInstanceOf(NullPointerException::class.java)
    }

    @Test
    fun `생성 요청의 기본값 - 상태는 PLANNED, 선택 필드는 null`() {
        val req = CreateEventRequest(title = "t", startAt = LocalDateTime.of(2026, 1, 1, 20, 0))
        assertThat(req.status).isEqualTo(EventStatus.PLANNED)
        assertThat(req.endAt).isNull()
        assertThat(req.targets).isNull()
        assertThat(req.imageUrls).isNull()
    }

    @Test
    fun `일별 요약 기본값은 0`() {
        val d = DaySummary(date = java.time.LocalDate.of(2026, 1, 1))
        assertThat(d.planned).isEqualTo(0)
        assertThat(d.completed).isEqualTo(0)
        assertThat(d.canceled).isEqualTo(0)
    }
}
