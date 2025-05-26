package com.project.byeoldori.user.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class RefreshToken(
    @Id
    val email: String,

    @Column(nullable = false)
    var token: String,

    @Column(nullable = false)
    var expiresAt: LocalDateTime
)