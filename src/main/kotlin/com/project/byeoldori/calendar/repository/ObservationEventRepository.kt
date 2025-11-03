package com.project.byeoldori.calendar.repository

import com.project.byeoldori.calendar.entity.ObservationEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.*

interface ObservationEventRepository : JpaRepository<ObservationEvent, Long> {
    fun findAllByUserIdAndStartAtBetweenOrderByStartAtAsc(
        userId: Long,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<ObservationEvent>

    fun findByIdAndUserId(id: Long, userId: Long): Optional<ObservationEvent>

    fun findStartStatusByUserIdAndStartAtBetween(
        userId: Long,
        from: LocalDateTime,
        to: LocalDateTime
    ): List<EventStartStatusView>

    fun findAllByUserIdAndStarObjectNameOrderByStartAtAsc(
        userId: Long, starObjectName: String
    ): List<ObservationEvent>

    fun findAllByUserIdAndStarObjectNameAndStartAtBetweenOrderByStartAtAsc(
        userId: Long, starObjectName: String, from: LocalDateTime, to: LocalDateTime
    ): List<ObservationEvent>
}