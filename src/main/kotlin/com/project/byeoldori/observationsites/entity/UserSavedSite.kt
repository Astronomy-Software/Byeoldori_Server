package com.project.byeoldori.observationsites.entity

import com.project.byeoldori.user.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
// 같은 사용자가 같은 공식 관측지를 두 번 저장하지 못하게 한다. V9 마이그레이션과 이름·컬럼을 맞춘다
@Table(
    name = "user_saved_sites",
    uniqueConstraints = [UniqueConstraint(name = "uk_user_saved_sites_user_site", columnNames = ["user_id", "site_id"])]
)
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