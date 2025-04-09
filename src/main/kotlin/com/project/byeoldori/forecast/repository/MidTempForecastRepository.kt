package com.project.byeoldori.forecast.repository

import com.project.byeoldori.forecast.entity.MidTempForecast
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface MidTempForecastRepository : JpaRepository<MidTempForecast, Long> {

    fun existsByTmFcAndTmEfAndRegId(tmFc: String, tmEf: String, regId: String): Boolean

    @Transactional
    fun deleteByCreatedAtBefore(cutoffTime: LocalDateTime): Long
}
