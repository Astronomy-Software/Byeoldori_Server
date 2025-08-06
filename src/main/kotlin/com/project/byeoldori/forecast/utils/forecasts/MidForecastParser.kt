package com.project.byeoldori.forecast.utils.forecasts

import com.project.byeoldori.forecast.entity.MidForecast
import org.springframework.stereotype.Component

@Component
class MidForecastParser {

    fun parse(response: String): List<MidForecast> {
        return response
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") } // 빈 줄, 주석 제거
            .map { line ->
                val columns = line.trim().split(Regex("\\s+")) // 공백 기준 split

                MidForecast(
                    regId = columns[0],
                    tmFc = columns[1],
                    tmEf = columns[2],
                    sky = columns[6],
                    pre = columns[7],
                    rnSt = columns[10].toIntOrNull() ?: -999 // 예외 처리
                )
            }
            .toList()
    }
}
