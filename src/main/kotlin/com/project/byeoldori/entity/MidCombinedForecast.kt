package com.project.byeoldori.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "mid_combined_forecast",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["tmFc", "tmEf", "siRegId"])
    ]
)

data class MidCombinedForecast(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    val tmFc: String,
    val tmEf: String,

    val doRegId: String,
    val siRegId: String,

    val sky: String?,
    val pre: String?,
    val wf: String?,
    val rnSt: Int?,

    val min: Int?,
    val max: Int?,

    @CreationTimestamp // 저장 시점이 자동으로 입력
    val createdAt: LocalDateTime? = null
)
