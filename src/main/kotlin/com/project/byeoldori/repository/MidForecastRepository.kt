package com.project.byeoldori.repository

import com.project.byeoldori.entity.MidForecast
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface MidForecastRepository : JpaRepository<MidForecast, Long> {
    fun existsByTmFcAndTmEfAndRegId(tmFc: String, tmEf: String, regId: String): Boolean

    @Transactional
    fun deleteByCreatedAtBefore(cutoffTime: LocalDateTime): Long
}

