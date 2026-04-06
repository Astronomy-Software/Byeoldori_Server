package com.project.byeoldori.notification.service

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.project.byeoldori.notification.repository.FcmTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(FirebaseApp::class)
class FcmNotificationService(
    private val fcmTokenRepository: FcmTokenRepository
) : NotificationService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun notifyNewComment(postAuthorId: Long, commenterName: String, preview: String) {
        sendToUser(
            userId = postAuthorId,
            title = "새 댓글",
            body = "${commenterName}님이 댓글을 남겼습니다: $preview"
        )
    }

    override fun notifyCommentLiked(commentAuthorId: Long, likerName: String) {
        sendToUser(
            userId = commentAuthorId,
            title = "댓글 좋아요",
            body = "${likerName}님이 내 댓글을 좋아합니다."
        )
    }

    override fun sendToUser(userId: Long, title: String, body: String) {
        val tokens = fcmTokenRepository.findTokensByUserId(userId)
        if (tokens.isEmpty()) return

        val message = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .build()

        try {
            val result = FirebaseMessaging.getInstance().sendEachForMulticast(message)
            if (result.failureCount > 0) {
                logger.warn("FCM 전송 일부 실패 - userId={}, 성공={}, 실패={}",
                    userId, result.successCount, result.failureCount)
            }
        } catch (e: FirebaseMessagingException) {
            logger.error("FCM 전송 오류 - userId={}: {}", userId, e.message)
        }
    }
}
