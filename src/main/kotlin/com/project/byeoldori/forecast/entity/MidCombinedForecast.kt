package com.project.byeoldori.forecast.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "mid_combined_forecast",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["tmFc", "tmEf", "siRegId"])
    ],
    // 조회(findBySiRegId...)용. 유니크 키 선두가 tm_fc 라 si_reg_id 로는 못 탄다. V8 마이그레이션과 맞춘다
    indexes = [Index(name = "idx_mid_combined_si_reg_tm_fc", columnList = "siRegId,tmFc")]
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
    val rnSt: Int?,

    val min: Int?,
    val max: Int?,

    @CreationTimestamp // 저장 시점이 자동으로 입력
    val createdAt: LocalDateTime? = null
)
