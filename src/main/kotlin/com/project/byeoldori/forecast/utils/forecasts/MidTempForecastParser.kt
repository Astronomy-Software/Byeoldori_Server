package com.project.byeoldori.forecast.utils.forecasts

import com.project.byeoldori.forecast.entity.MidTempForecast
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MidTempForecastParser {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun parse(response: String): List<MidTempForecast> {
        var skipped = 0
        val forecasts = response
            .lineSequence()                                // 줄 단위로 읽고
            .filter { it.isNotBlank() && !it.startsWith("#") } // 빈 줄, 주석 제거
            .mapNotNull { line ->
                val columns = line.trim().split(Regex("\\s+"))  // 공백 기준 분리
                // 열이 모자란 줄(안내 문구 등) 하나 때문에 나머지 정상 줄까지 버리지 않도록 그 줄만 건너뛴다
                if (columns.size < MIN_COLUMNS) {
                    skipped++
                    return@mapNotNull null
                }

                MidTempForecast(
                    regId = columns[0],
                    tmFc = columns[1],
                    tmEf = columns[2],
                    min = columns[6].toIntOrNull() ?: 0,
                    max = columns[7].toIntOrNull() ?: 0
                )
            }
            .toList()
        if (skipped > 0) {
            // 정상 줄이 하나도 없으면 응답 자체가 잘못된 것이라 예외로 두어 스케줄러가 재시도하게 한다
            check(forecasts.isNotEmpty()) { "중기 기온 예보 응답에 정상 줄이 없습니다. (형식이 다른 줄 ${skipped}개)" }
            logger.warn("중기 기온 예보 응답에서 형식이 다른 줄 {}개를 건너뛰었습니다.", skipped)
        }
        return forecasts
    }

    companion object {
        private const val MIN_COLUMNS = 8
    }
}
