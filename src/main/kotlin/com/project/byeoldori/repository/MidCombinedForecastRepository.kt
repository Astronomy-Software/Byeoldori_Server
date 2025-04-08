package com.project.byeoldori.repository

import com.project.byeoldori.entity.MidCombinedForecast
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface MidCombinedForecastRepository : JpaRepository<MidCombinedForecast, Long> {

    fun existsByTmFcAndTmEfAndSiRegId(tmFc: String, tmEf: String, siRegId: String): Boolean

    @Transactional
    fun deleteByCreatedAtBefore(cutoffTime: LocalDateTime): Long
}
