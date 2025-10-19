package com.project.byeoldori.calendar.repository

import com.project.byeoldori.calendar.entity.CalendarImage
import org.springframework.data.jpa.repository.JpaRepository

interface CalendarImageRepository : JpaRepository<CalendarImage, Long> {
    fun findAllByEventIdOrderByIdAsc(eventId: Long): List<CalendarImage>

    fun findAllByEventIdIn(eventIds: List<Long>): List<CalendarImage>
}