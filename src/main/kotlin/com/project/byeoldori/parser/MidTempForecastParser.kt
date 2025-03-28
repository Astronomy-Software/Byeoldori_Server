package com.project.byeoldori.parser

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
                    min = columns[6],
                    max = columns[7],
                    minL = columns[8],
                    minH = columns[9],
                    maxL = columns[10],
                    maxH = columns[11]
                )
            }
            .toList()
    }
}