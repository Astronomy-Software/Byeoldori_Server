package com.project.byeoldori.notification.dto

import com.project.byeoldori.notification.entity.Notification
import com.project.byeoldori.notification.entity.NotificationType
import java.time.LocalDateTime

data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: LocalDateTime
)

fun Notification.toResponse() = NotificationResponse(
    id = id,
    type = type,
    title = title,
    body = body,
    isRead = isRead,
    createdAt = createdAt
)
