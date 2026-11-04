package com.project.byeoldori.notification.service

import com.project.byeoldori.notification.entity.Notification
import com.project.byeoldori.notification.repository.NotificationRepository
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDateTime
import java.util.Optional

class DbNotificationServiceTest {
    private val notificationRepository = mock(NotificationRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val service = DbNotificationService(notificationRepository, userRepository)

    private fun user(id: Long, deleted: Boolean) =
        User(id = id, email = "u$id@test.com", passwordHash = "x", name = "이름", phone = "")
            .apply { if (deleted) deletedAt = LocalDateTime.now() }

    @Test
    fun `탈퇴 계정 앞으로는 댓글·좋아요 알림을 쌓지 않는다`() {
        `when`(userRepository.findById(1L)).thenReturn(Optional.of(user(1, deleted = true)))

        service.notifyNewComment(1L, "별지기", "안녕하세요")
        service.notifyCommentLiked(1L, "별지기")

        verify(notificationRepository, never()).save(any(Notification::class.java))
    }

    @Test
    fun `활성 계정에는 알림을 저장한다`() {
        `when`(userRepository.findById(2L)).thenReturn(Optional.of(user(2, deleted = false)))

        service.notifyNewComment(2L, "별지기", "안녕하세요")

        verify(notificationRepository, times(1)).save(any(Notification::class.java))
    }
}
