package com.project.byeoldori.forecast.utils.score

import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToLong

@Component
class LightPollution {

    private val pollutionData: List<Triple<Double, Double, Double>> = loadCsv()

    // 0.05도(약 5km) 격자 키 → O(1) 탐색
    private val pollutionGrid: Map<String, Double> = pollutionData.associate { (lat, lon, value) ->
        gridKey(lat, lon) to value
    }

    @Cacheable("lightPollution", key = "#lat + ',' + #lon")
    fun getLightPollutionScore(lat: Double, lon: Double): Double {
        if (lat !in 33.0..39.5 || lon !in 124.0..132.0) {
            throw IllegalArgumentException("지원되지 않는 지역입니다: ($lat, $lon)")
        }

        val value = pollutionGrid[gridKey(lat, lon)]
            ?: pollutionData.minByOrNull { (plat, plon, _) -> hypot(lat - plat, lon - plon) }?.third
            ?: throw IllegalStateException("광공해 데이터가 비어 있습니다.")

        return convertToScore(value)
    }

    private fun gridKey(lat: Double, lon: Double): String {
        val r = 1.0 / 0.05
        return "${(lat * r).roundToLong()},${(lon * r).roundToLong()}"
    }

    private fun loadCsv(): List<Triple<Double, Double, Double>> {
        val inputStream = this::class.java.getResourceAsStream("/light_pollution_korea_5km_grid.csv")
            ?: throw IllegalStateException("광공해 CSV 파일이 누락되었습니다.")

        return inputStream.bufferedReader().lineSequence()
            .drop(1)  // 첫 줄 헤더 제거
            .map { line ->
                val (lat, lon, value) = line.split(",")
                Triple(lat.toDouble(), lon.toDouble(), value.toDouble())
            }.toList()
    }


    // 광공해 수치 로그스케일 점수로 환산
    private fun convertToScore(value: Double): Double {
        val safeValue = (value + 1).coerceAtLeast(1.0)  // ln(0) 방지
        val score = 1.0 - ln(safeValue) / ln(259.144)   // 259.144 = 최대값 + 1
        return score.coerceIn(0.0, 1.0)
    }
}
