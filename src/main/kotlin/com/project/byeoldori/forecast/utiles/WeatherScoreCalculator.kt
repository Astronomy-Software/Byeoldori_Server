package com.project.byeoldori.forecast.utiles

import com.project.byeoldori.forecast.dto.ForecastResponseDTO
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class WeatherScoreCalculator {

    enum class ForecastType { ULTRA, SHORT, MID }

    fun getForecastType(observationTime: LocalDateTime): ForecastType {
        val now = LocalDateTime.now()
        val hours = java.time.Duration.between(now, observationTime).toHours()
        return when {
            hours <= 6 -> ForecastType.ULTRA
            hours <= 72 -> ForecastType.SHORT
            else -> ForecastType.MID
        }
    }

    private fun formatToForecastTMEF(time: LocalDateTime): String =
        time.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))

    fun getSkyScore(forecast: ForecastResponseDTO, type: ForecastType, time: LocalDateTime): Double {
        val tStr = formatToForecastTMEF(time)
        return when (type) {
            ForecastType.ULTRA -> forecast.ultraForecastResponse.find { it.tmef == tStr }?.sky?.toInt()
            ForecastType.SHORT -> forecast.shortForecastResponse.find { it.tmef == tStr }?.sky?.toInt()
            ForecastType.MID -> when (forecast.midCombinedForecastDTO.find { it.tmEf.startsWith(tStr.take(8)) }?.sky) {
                "WB01" -> 1
                "WB02" -> 2
                "WB03" -> 3
                "WB04" -> 4
                else -> null
            }
        }?.let {
            when (it) {
                1 -> 1.0
                2 -> 0.66
                3 -> 0.33
                4 -> 0.0
                else -> 0.5
            }
        } ?: 0.5
    }

    fun getPreScore(forecast: ForecastResponseDTO, type: ForecastType, time: LocalDateTime): Double {
        val tStr = formatToForecastTMEF(time)
        return when (type) {
            ForecastType.ULTRA -> forecast.ultraForecastResponse.find { it.tmef == tStr }?.pty
            ForecastType.SHORT -> forecast.shortForecastResponse.find { it.tmef == tStr }?.pty
            ForecastType.MID -> when (forecast.midCombinedForecastDTO.find { it.tmEf.startsWith(tStr.take(8)) }?.pre) {
                "WB00" -> 0.0
                else -> 1.0
            }
        }?.let {
            if (it == 0.0) 1.0 else 0.0
        } ?: 0.5
    }

    fun getWindScore(forecast: ForecastResponseDTO, type: ForecastType, time: LocalDateTime): Double? {
        val tStr = formatToForecastTMEF(time)
        val wsd = when (type) {
            ForecastType.ULTRA -> forecast.ultraForecastResponse.find { it.tmef == tStr }?.wsd
            ForecastType.SHORT -> forecast.shortForecastResponse.find { it.tmef == tStr }?.wsd
            ForecastType.MID -> null
        }

        return wsd?.let {
            val decayFactor = 5.0
            1.0 / (1.0 + (it / decayFactor))
        }
    }
}
