package com.project.byeoldori.notification.entity

import com.project.byeoldori.common.jpa.BaseTimeEntity
import com.project.byeoldori.user.entity.User
import jakarta.persistence.*

enum class NotificationType {
    NEW_COMMENT, COMMENT_LIKED, SYSTEM
}

@Entity
@Table(name = "notifications")
class Notification(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val type: NotificationType,

    @Column(nullable = false, length = 128)
    val title: String,

    @Column(nullable = false, length = 512)
    val body: String,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false

) : BaseTimeEntity()
