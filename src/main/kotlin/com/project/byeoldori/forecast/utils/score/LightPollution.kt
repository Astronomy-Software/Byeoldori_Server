package com.project.byeoldori.forecast.utils.score

import org.springframework.stereotype.Component
import kotlin.math.hypot
import kotlin.math.ln

@Component
class LightPollution {

    val pollutionData: List<Triple<Double, Double, Double>> = loadCsv()

    fun getLightPollutionScore(lat: Double, lon: Double): Double {
        if (lat !in 33.0..39.5 || lon !in 124.0..132.0) {
            throw IllegalArgumentException("지원되지 않는 지역입니다: ($lat, $lon)")
        }

        val nearest = pollutionData.minByOrNull { (plat, plon, _) ->
            hypot(lat - plat, lon - plon) // 가장 가까운 지점 탐색
        } ?: throw IllegalStateException("광공해 데이터가 비어 있습니다.")

        return convertToScore(nearest.third)
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
