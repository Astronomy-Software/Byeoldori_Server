package com.project.byeoldori.forecast.utils.forecasts

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ForecastTimeUtil {

    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    /**
     * 초단기예보에서 유효한 발표시각(tmfc)을 계산
     * 현재 시각 기준 60분 전의 가장 가까운 30분 단위 시각을 반환
     */
    fun getStableUltraTmfc(now: LocalDateTime = LocalDateTime.now()): String {
        // 60분 전 기준으로 00 or 30분으로 맞춤
        val adjusted = now.minusMinutes(60)
        val minute = if (adjusted.minute < 30) 0 else 30
        val stable = adjusted.withMinute(minute).withSecond(0).withNano(0)
        return stable.format(formatter)
    }

    /**
     * 예보 시각 리스트 (현재 시각 기준 이후 5시간)
     */
    fun getNext6UltraTmef(now: LocalDateTime = LocalDateTime.now()): List<String> {
        return (1..6).map {
            now.plusHours(it.toLong())
                .withMinute(0).withSecond(0).withNano(0)
                .format(formatter)
        }
    }
}