package com.project.byeoldori.calendar.repository

import com.project.byeoldori.calendar.entity.EventStatus
import java.time.LocalDateTime

interface EventStartStatusView {
    val startAt: LocalDateTime
    val status: EventStatus
}