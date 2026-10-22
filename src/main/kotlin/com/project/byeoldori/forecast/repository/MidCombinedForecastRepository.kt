package com.project.byeoldori.forecast.repository

import com.project.byeoldori.forecast.entity.MidCombinedForecast
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface MidCombinedForecastRepository : JpaRepository<MidCombinedForecast, Long> {

    fun existsByTmFcAndTmEfAndSiRegId(tmFc: String, tmEf: String, siRegId: String): Boolean
    fun findByTmFcAndTmEfAndSiRegId(tmFc: String, tmEf: String, siRegId: String): MidCombinedForecast?
    // 같은 tmEf 는 최신 발표(tmFc 내림차순)가 먼저 오도록 정렬해 읽는다 — 서비스에서 tmEf 별 첫 행만 남긴다
    fun findBySiRegIdOrderByTmEfAscTmFcDesc(siRegId: String): List<MidCombinedForecast>

    @Transactional
    fun deleteByCreatedAtBefore(cutoffTime: LocalDateTime): Long
}
