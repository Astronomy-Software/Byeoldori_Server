package com.project.byeoldori.notification.service

import com.project.byeoldori.notification.entity.Notification
import com.project.byeoldori.notification.entity.NotificationType
import com.project.byeoldori.notification.repository.NotificationRepository
import com.project.byeoldori.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DbNotificationService(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository
) : NotificationService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun notifyNewComment(postAuthorId: Long, commenterName: String, preview: String) {
        sendToUser(
            userId = postAuthorId,
            title = "새 댓글",
            body = "$commenterName 님이 댓글을 남겼습니다: $preview",
            type = NotificationType.NEW_COMMENT
        )
    }

    @Transactional
    override fun notifyCommentLiked(commentAuthorId: Long, likerName: String) {
        sendToUser(
            userId = commentAuthorId,
            title = "댓글 좋아요",
            body = "$likerName 님이 내 댓글을 좋아합니다.",
            type = NotificationType.COMMENT_LIKED
        )
    }

    @Transactional
    override fun sendToUser(userId: Long, title: String, body: String) {
        sendToUser(userId, title, body, NotificationType.SYSTEM)
    }

    private fun sendToUser(userId: Long, title: String, body: String, type: NotificationType) {
        val user = userRepository.findById(userId).orElse(null) ?: run {
            log.warn("알림 전송 실패 - 사용자 없음: userId={}", userId)
            return
        }
        // 탈퇴 계정 앞으로는 쌓지 않는다(남은 공개 글에 달린 댓글·좋아요 알림에 다른 사람 이름·내용이 담기므로)
        if (user.deletedAt != null) return
        notificationRepository.save(Notification(user = user, type = type, title = title, body = body))
    }
}
