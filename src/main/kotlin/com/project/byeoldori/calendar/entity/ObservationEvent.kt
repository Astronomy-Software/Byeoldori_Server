package com.project.byeoldori.calendar.entity

import com.project.byeoldori.common.jpa.BaseTimeEntity
import com.project.byeoldori.observationsites.entity.ObservationSite
import jakarta.persistence.*
import java.time.LocalDateTime

enum class EventStatus { PLANNED, COMPLETED, CANCELED } // PLANNED는 계획, COMPLETED는 완료/기록, CANCELED는 계획 취소

@Entity
@Table(
    name = "observation_event",
    indexes = [Index(name = "idx_event_user_start", columnList = "userId,startAt")]
)
class ObservationEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    var title: String,

    var targetName: String? = null,

    @Column(nullable = false)
    var startAt: LocalDateTime,

    var endAt: LocalDateTime? = null,

    var lat: Double? = null,
    var lon: Double? = null,
    var placeName: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observation_site_id")
    var observationSite: ObservationSite? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EventStatus = EventStatus.PLANNED,

    @Column(columnDefinition = "TEXT")
    var memo: String? = null
) : BaseTimeEntity()