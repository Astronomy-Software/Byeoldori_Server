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
     * 예보 시각 리스트 (현재 시각 기준 이후 6시간)
     */
    fun getNext6UltraTmef(now: LocalDateTime = LocalDateTime.now()): List<String> {
        return (1..6).map {
            now.plusHours(it.toLong())
                .withMinute(0).withSecond(0).withNano(0)
                .format(formatter)
        }
    }

    fun getTMFCTimeForShort(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

        // 패턴 시간(2,5,8,11,14,17,20,23)
        val patternHours = setOf(2, 5, 8, 11, 14, 17, 20, 23)

        // 1) 분/초/나노초를 0으로 맞춤 (정시 기준)
        var dateTime = current
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        // 2) "가장 가까운" 패턴 시각으로 이동
        //    - 현재 시각의 시(hour)가 patternHours에 없다면, 1시간씩 더하며 맞춤
        while (dateTime.hour !in patternHours) {
            dateTime = dateTime.minusHours(1)
        }

        // 3) 포맷팅하여 반환
        return dateTime.format(formatter)
    }

    fun getTMEFTimesForShortForecast(): List<String> {
        // 현재 시각
        var now = LocalDateTime.now()
        val patternHours = setOf(2, 5, 8, 11, 14, 17, 20, 23)

        // 1) 분(MM)이 0이 아니라면 다음 정시로 맞춤
        if (now.minute != 0) {
            now = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        } else {
            // 이미 정각이라면, 초/나노초만 0으로 맞춤
            now = now.withSecond(0).withNano(0)
        }

        // 2) 가장 가까운 패턴 시각(2,5,8,11,14,17,20,23)을 찾을 때까지 1시간씩 증가
        while (now.hour !in patternHours) {
            now = now.minusHours(1)
        }

        // 3) 패턴에 따라 종료 시각 결정
        val set1 = setOf(2, 5, 8, 11, 14)   // +2일 자정
        val set2 = setOf(17, 20, 23)       // +3일 자정

        val endDateTime = if (now.hour in set1) {
            now.toLocalDate().plusDays(3).atStartOfDay()
        } else {
            now.toLocalDate().plusDays(4).atStartOfDay()
        }

        // ─────────────────────────────────────────────────────
        //        // 4) 1시간 후부터 리스트 생성
        // ─────────────────────────────────────────────────────
        // now가 "가장 가까운 패턴 시각"이므로, 거기서 +1시간 한 시점부터 시작
        var temp = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)

        // 결과 리스트
        val result = mutableListOf<LocalDateTime>()

        // endDateTime까지 1시간 간격으로 추가
        while (!temp.isAfter(endDateTime)) {
            result.add(temp)
            temp = temp.plusHours(1)
        }

        // 5) 포맷팅 (yyyyMMddHHmm)
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        return result.map { it.format(formatter) }
    }
}