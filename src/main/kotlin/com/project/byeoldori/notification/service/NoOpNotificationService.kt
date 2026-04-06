package com.project.byeoldori.notification.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Service

@Service
@ConditionalOnMissingBean(FcmNotificationService::class)
class NoOpNotificationService : NotificationService {
    override fun notifyNewComment(postAuthorId: Long, commenterName: String, preview: String) {}
    override fun notifyCommentLiked(commentAuthorId: Long, likerName: String) {}
    override fun sendToUser(userId: Long, title: String, body: String) {}
}
