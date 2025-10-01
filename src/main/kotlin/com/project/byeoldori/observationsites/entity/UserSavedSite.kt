package com.project.byeoldori.observationsites.entity

import com.project.byeoldori.user.entity.User
import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "user_saved_sites")
@IdClass(UserSavedSiteId::class)
class UserSavedSite(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    val site: ObservationSite,
    val savedAt: LocalDateTime = LocalDateTime.now()
)

data class UserSavedSiteId(
    var user: Long = 0L,
    var site: Long = 0L
) : Serializable