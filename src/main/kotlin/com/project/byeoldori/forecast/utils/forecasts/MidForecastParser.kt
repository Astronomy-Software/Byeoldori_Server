package com.project.byeoldori.forecast.utils.forecasts

import com.project.byeoldori.forecast.entity.MidForecast
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MidForecastParser {

    private val logger = LoggerFactory.getLogger(this::class.java)

    fun parse(response: String): List<MidForecast> {
        var skipped = 0
        val forecasts = response
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") } // 빈 줄, 주석 제거
            .mapNotNull { line ->
                val columns = line.trim().split(Regex("\\s+")) // 공백 기준 split
                // 열이 모자란 줄(안내 문구 등) 하나 때문에 나머지 정상 줄까지 버리지 않도록 그 줄만 건너뛴다
                if (columns.size < MIN_COLUMNS) {
                    skipped++
                    return@mapNotNull null
                }

                MidForecast(
                    regId = columns[0],
                    tmFc = columns[1],
                    tmEf = columns[2],
                    sky = columns[6],
                    pre = columns[7],
                    rnSt = columns[10].toIntOrNull() ?: 0
                )
            }
            .toList()
        // 정상 줄이 하나도 없으면(오류 문구만, 주석만, 빈 본문) 응답 자체가 잘못된 것이라 예외로 두어 스케줄러가 재시도하게 한다.
        // 빈 본문을 빈 목록으로 돌려주면 "성공"으로 끝나 저장 0건인데 재시도도 없다(2026-11-27 06:11, 기상청 200+빈 본문).
        check(forecasts.isNotEmpty()) {
            "중기 육상 예보 응답에 정상 줄이 없습니다. (형식이 다른 줄 ${skipped}개, 본문: ${ResponseBodySummary.of(response)})"
        }
        if (skipped > 0) logger.warn("중기 육상 예보 응답에서 형식이 다른 줄 {}개를 건너뛰었습니다.", skipped)
        return forecasts
    }

    companion object {
        private const val MIN_COLUMNS = 11
    }
}
