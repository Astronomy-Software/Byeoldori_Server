package com.project.byeoldori.domain.midforecast

import org.springframework.data.jpa.repository.JpaRepository

interface MidForecastRepository : JpaRepository<MidForecast, Long> {
    fun existsByTmFcAndTmEfAndRegId(tmFc: String, tmEf: String, regId: String): Boolean
}

