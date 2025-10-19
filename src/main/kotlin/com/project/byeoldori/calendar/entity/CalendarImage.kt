package com.project.byeoldori.calendar.entity

import com.project.byeoldori.common.jpa.BaseTimeEntity
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "calendar_image", indexes = [Index(name = "idx_photo_event", columnList = "event_id")])
class CalendarImage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var event: ObservationEvent,

    @Column(nullable = false, length = 512)
    var url: String,

    @Column(name = "content_type")
    var contentType: String? = null
) : BaseTimeEntity()