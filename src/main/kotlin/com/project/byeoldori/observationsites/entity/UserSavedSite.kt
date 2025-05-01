package com.project.byeoldori.observationsites.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_saved_site",
    uniqueConstraints = [UniqueConstraint(columnNames = ["userId", "siteId"])]
)
data class UserSavedSite(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val userId: String,         // 사용자 ID (로그인 없으면 디바이스 ID나 UUID도 가능)
    val siteId: Long,           // ObservationSite ID

    val savedAt: LocalDateTime = LocalDateTime.now()
)
