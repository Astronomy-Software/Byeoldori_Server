package com.project.byeoldori.parser

import com.project.byeoldori.entity.MidForecast
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
                    modCode = columns[3],
                    stn = columns[4],
                    c = columns[5],
                    sky = columns[6],
                    pre = columns[7],
                    conf = columns[8],
                    wf = columns[9].replace("\"", ""), // 큰따옴표 제거
                    rnSt = columns[10].toIntOrNull() ?: -1 // 예외 처리
                )
            }
            .toList()
    }
}
