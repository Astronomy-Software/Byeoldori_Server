package com.project.byeoldori.repository

import com.project.byeoldori.entity.MidTempForecast
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface MidTempForecastRepository : JpaRepository<MidTempForecast, Long> {

    fun existsByTmFcAndTmEfAndRegId(tmFc: String, tmEf: String, regId: String): Boolean

    @Transactional
    fun deleteByCreatedAtBefore(cutoffTime: LocalDateTime): Long
}
