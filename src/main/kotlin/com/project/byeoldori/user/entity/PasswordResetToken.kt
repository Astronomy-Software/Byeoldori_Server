package com.project.byeoldori.user.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
data class PasswordResetToken(
    @Id
    val token: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val email: String,

    @Column(nullable = false)
    val expiration: LocalDateTime = LocalDateTime.now().plusMinutes(10),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var used: Boolean = false
)

