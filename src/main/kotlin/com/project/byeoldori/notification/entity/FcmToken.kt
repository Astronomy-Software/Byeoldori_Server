package com.project.byeoldori.notification.entity

import com.project.byeoldori.common.jpa.BaseTimeEntity
import com.project.byeoldori.user.entity.User
import jakarta.persistence.*

@Entity
@Table(
    name = "fcm_tokens",
    uniqueConstraints = [UniqueConstraint(columnNames = ["token"])]
)
class FcmToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 256)
    val token: String,

    @Column(length = 16)
    val deviceType: String = "android"
) : BaseTimeEntity()
