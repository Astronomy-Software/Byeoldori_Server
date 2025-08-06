package com.project.byeoldori.forecast.utils.forecasts

import com.project.byeoldori.forecast.entity.MidTempForecast
import org.springframework.stereotype.Component

@Component
class MidTempForecastParser {

    fun parse(response: String): List<MidTempForecast> {
        return response
            .lineSequence()                                // 줄 단위로 읽고
            .filter { it.isNotBlank() && !it.startsWith("#") } // 빈 줄, 주석 제거
            .map { line ->
                val columns = line.trim().split(Regex("\\s+"))  // 공백 기준 분리

                MidTempForecast(
                    regId = columns[0],
                    tmFc = columns[1],
                    tmEf = columns[2],
                    min = columns[6].toIntOrNull() ?: -999,
                    max = columns[7].toIntOrNull() ?: -999
                )
            }
            .toList()
    }
}