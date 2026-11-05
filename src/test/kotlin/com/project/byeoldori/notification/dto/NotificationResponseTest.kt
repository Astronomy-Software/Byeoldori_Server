package com.project.byeoldori.notification.dto

import com.project.byeoldori.notification.entity.Notification
import com.project.byeoldori.notification.entity.NotificationType
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotificationResponseTest {

    private val user = User(id = 1, email = "u@test.com", passwordHash = "x", name = "u", phone = "010")

    @Test
    fun `알림 엔티티를 응답으로 옮기고 수신자 정보는 노출하지 않는다`() {
        val n = Notification(id = 9, user = user, type = NotificationType.NEW_COMMENT, title = "새 댓글", body = "별지기 님이 댓글을 남겼습니다: 안녕", isRead = true)

        val res = n.toResponse()

        assertThat(res.id).isEqualTo(9)
        assertThat(res.type).isEqualTo(NotificationType.NEW_COMMENT)
        assertThat(res.title).isEqualTo("새 댓글")
        assertThat(res.body).isEqualTo("별지기 님이 댓글을 남겼습니다: 안녕")
        assertThat(res.isRead).isTrue()
        assertThat(res.createdAt).isEqualTo(n.createdAt)
        assertThat(NotificationResponse::class.java.declaredFields.map { it.name }).doesNotContain("user", "userId")
    }

    @Test
    fun `읽음 기본값은 false`() {
        val n = Notification(user = user, type = NotificationType.SYSTEM, title = "t", body = "b")
        assertThat(n.toResponse().isRead).isFalse()
        assertThat(n.toResponse().id).isEqualTo(0)
    }

    @Test
    fun `모든 알림 타입을 변환할 수 있다`() {
        NotificationType.entries.forEach { type ->
            assertThat(Notification(user = user, type = type, title = "t", body = "b").toResponse().type).isEqualTo(type)
        }
    }
}
