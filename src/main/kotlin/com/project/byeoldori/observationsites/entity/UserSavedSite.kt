package com.project.byeoldori.observationsites.entity

import com.project.byeoldori.user.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "user_saved_sites")
class UserSavedSite(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    val site: ObservationSite? = null,

    // 사용자가 임의의 장소 즐겨찾기
    @Column(name = "custom_name")
    var customName: String? = null,

    @Column(name = "custom_latitude")
    var customLatitude: Double? = null,

    @Column(name = "custom_longitude")
    var customLongitude: Double? = null,

    val savedAt: LocalDateTime = LocalDateTime.now()
)