package com.project.byeoldori.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "mid_forecast")
data class MidForecast(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val regId: String,

    @Column(nullable = false)
    val tmFc: String,

    @Column(nullable = false)
    val tmEf: String,

    @Column(nullable = false)
    val sky: String,

    @Column(nullable = false)
    val pre: String,

    @Column(nullable = false)
    val rnSt: Int,

    @CreationTimestamp // 저장 시점이 자동으로 입력
    val createdAt: LocalDateTime? = null
)