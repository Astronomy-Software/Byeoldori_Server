package com.project.byeoldori.repository

import com.project.byeoldori.entity.MidCombinedForecast
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MidCombinedForecastRepository : JpaRepository<MidCombinedForecast, Long> {

    fun existsByTmFcAndTmEfAndSiRegId(tmFc: String, tmEf: String, siRegId: String): Boolean

    fun findByTmFcAndTmEfAndSiRegId(tmFc: String, tmEf: String, siRegId: String): MidCombinedForecast?
}
