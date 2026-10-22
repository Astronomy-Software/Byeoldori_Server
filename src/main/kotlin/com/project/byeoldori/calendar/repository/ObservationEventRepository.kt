package com.project.byeoldori.calendar.repository

import com.project.byeoldori.calendar.entity.EventStatus
import com.project.byeoldori.calendar.entity.ObservationEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.*

interface ObservationEventRepository : JpaRepository<ObservationEvent, Long> {
    // [from, to) 반열린 구간. Between 은 양 끝을 포함해 다음 날 00:00 일정이 전날 목록에도 나왔다.
    fun findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
        userId: Long,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<ObservationEvent>

    fun findByIdAndUserId(id: Long, userId: Long): Optional<ObservationEvent>

    fun findStartStatusByUserIdAndStartAtGreaterThanEqualAndStartAtLessThan(
        userId: Long,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<EventStartStatusView>

    fun countByUserIdAndStatus(userId: Long, status: EventStatus): Long

    // 관측지가 삭제될 때 캘린더 기록과의 연결을 끊는다(lat/lon/placeName 은 기록에 이미 복사돼 있다).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ObservationEvent e set e.observationSite = null where e.observationSite.id = :siteId")
    fun detachObservationSite(@Param("siteId") siteId: Long): Int
}
