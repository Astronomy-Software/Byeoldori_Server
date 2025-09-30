package com.project.byeoldori.forecast.utils.forecasts

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ForecastTimeUtil {

    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    private fun nowKst(): ZonedDateTime = ZonedDateTime.now(SEOUL)

    /**
     * 초단기예보에서 유효한 발표시각(tmfc)을 계산
     * 현재 시각 기준 60분 전의 가장 가까운 30분 단위 시각을 반환
     */
    fun getStableUltraTmfc(now: ZonedDateTime = nowKst()): String {
        // 60분 전 기준으로 00 or 30분으로 맞춤
        val adjusted = now.minusMinutes(60)
        val minute = if (adjusted.minute < 30) 0 else 30
        val stable = adjusted.withMinute(minute).withSecond(0).withNano(0)
        return stable.format(formatter)
    }

    /**
     * 예보 시각 리스트 (현재 시각 기준 이후 5시간)
     */
    fun getNext6UltraTmef(now: ZonedDateTime = nowKst()): List<String> {
        return (1..6).map {
            now.plusHours(it.toLong())
                .withMinute(0).withSecond(0).withNano(0)
                .format(formatter)
        }
    }

    /**
     * 단기예보에서 유효한 발표시각(tmfc)을 계산합니다.
     * API는 2, 5, 8, 11, 14, 17, 20, 23시에 데이터를 발표합니다.
     */
    fun getStableShortTmfc(now: ZonedDateTime = nowKst()): String {
        val baseTimes = listOf(
            LocalTime.of(2, 10), LocalTime.of(5, 10), LocalTime.of(8, 10),
            LocalTime.of(11, 10), LocalTime.of(14, 10), LocalTime.of(17, 10),
            LocalTime.of(20, 10), LocalTime.of(23, 10)
        )
        var baseDateTime = now

        for (i in baseTimes.indices.reversed()) {
            val baseTime = baseTimes[i]
            val potentialBase = now.with(baseTime)
            if (now.isAfter(potentialBase)) {
                baseDateTime = potentialBase
                break
            }
            if (i == 0) {
                baseDateTime = now.minusDays(1).with(baseTimes.last())
            }
        }
        return baseDateTime.withMinute(0).format(formatter)
    }

    /**
     * 단기 예보 시각 리스트 (현재 시각 기준 3시간 단위로 24개)
     */
    fun getNext24ShortTmef(now: ZonedDateTime = nowKst()): List<String> {
        val startHour = (now.hour / 3) * 3
        var current = now.withHour(startHour).withMinute(0).withSecond(0).withNano(0)

        if(current.isBefore(now)) {
            current = current.plusHours(3)
        }

        return (0 until 24).map {
            current.plusHours(it * 3L).format(formatter)
        }
    }
}