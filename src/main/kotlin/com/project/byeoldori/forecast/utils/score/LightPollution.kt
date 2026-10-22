package com.project.byeoldori.forecast.utils.score

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
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

    // 데이터에 없는 격자 칸(바다 등)의 가장 가까운 값. 키는 지원 범위 안의 격자 칸이라 개수에 상한이 있다(약 2만 개)
    private val nearestByCell = ConcurrentHashMap<String, Double>()

    /**
     * 예전에는 요청 위경도(double) 그대로를 Redis 캐시 키로 써서(@Cacheable), 좌표만 바꿔 부르면
     * 키가 상한 없이 늘었다(공개 API·rate limit 없음·Redis maxmemory 없음). 계산은 메모리 맵 조회라
     * Redis 왕복보다 싸므로 캐시를 없애고, 느린 최근접 탐색만 격자 칸 단위로 메모리에 기억한다.
     */
    fun getLightPollutionScore(lat: Double, lon: Double): Double {
        if (lat !in 33.0..39.5 || lon !in 124.0..132.0) {
            throw IllegalArgumentException("지원되지 않는 지역입니다: ($lat, $lon)")
        }

        val key = gridKey(lat, lon)
        val value = pollutionGrid[key] ?: nearestByCell.computeIfAbsent(key) { nearestToCellCenter(lat, lon) }

        return convertToScore(value)
    }

    // 같은 칸이면 어떤 좌표로 먼저 물어도 같은 값이 나오도록 칸 중심 기준으로 찾는다
    private fun nearestToCellCenter(lat: Double, lon: Double): Double {
        val clat = (lat * GRID_RES).roundToLong() / GRID_RES
        val clon = (lon * GRID_RES).roundToLong() / GRID_RES
        return pollutionData.minByOrNull { (plat, plon, _) -> hypot(clat - plat, clon - plon) }?.third
            ?: throw IllegalStateException("광공해 데이터가 비어 있습니다.")
    }

    private fun gridKey(lat: Double, lon: Double): String =
        "${(lat * GRID_RES).roundToLong()},${(lon * GRID_RES).roundToLong()}"

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


    companion object {
        // 0.05도 격자 → 1도에 20칸
        private const val GRID_RES = 1.0 / 0.05
    }

    // 광공해 수치 로그스케일 점수로 환산
    private fun convertToScore(value: Double): Double {
        val safeValue = (value + 1).coerceAtLeast(1.0)  // ln(0) 방지
        val score = 1.0 - ln(safeValue) / ln(259.144)   // 259.144 = 최대값 + 1
        return score.coerceIn(0.0, 1.0)
    }
}
