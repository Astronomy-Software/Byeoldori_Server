package com.project.byeoldori.forecast.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "mid_temp_forecast")
data class MidTempForecast(
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
    val min: Int,

    @Column(nullable = false)
    val max: Int,

    @CreationTimestamp
    val createdAt: LocalDateTime? = null
)
