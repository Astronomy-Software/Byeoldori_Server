package com.project.byeoldori.utiles

import com.project.byeoldori.entity.MidTempForecast
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
                    modCode = columns[3],
                    stn = columns[4],
                    c = columns[5],
                    min = columns[6].toIntOrNull() ?: -999,
                    max = columns[7].toIntOrNull() ?: -999,
                    minL = columns[8].toIntOrNull() ?: -999,
                    minH = columns[9].toIntOrNull() ?: -999,
                    maxL = columns[10].toIntOrNull() ?: -999,
                    maxH = columns[11].toIntOrNull() ?: -999
                )
            }
            .toList()
    }
}