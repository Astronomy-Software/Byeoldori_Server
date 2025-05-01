package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.dto.ForecastResponseDTO
import com.project.byeoldori.forecast.dto.UltraForecastResponseDTO
import com.project.byeoldori.forecast.dto.ShortForecastResponseDTO
import com.project.byeoldori.forecast.utiles.RegionMapper
import latLonToGrid
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Service
class ForeCastService(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val midCombinedForecastService: MidCombinedForecastService
) {
    // 예보 타입 정의
    enum class ForecastType { ULTRA, SHORT, MID }

    fun getForecastDataByLocation(latitude: Double, longitude: Double): ForecastResponseDTO {

        // 1) 위경도 -> 격자x 좌표 변환
        val (x, y) = latLonToGrid(latitude, longitude)

        // 1-2) 위경도 -> 좌표로 변환 후 시 지역 코드로 매핑
        val siRegId = RegionMapper.getSiByLatLon(latitude, longitude) ?: "UNKNOWN"

        // 2) 초단기, 단기 예보
        val ultraForecast: List<UltraForecastResponseDTO> = ultraGridForecastService.getAllUltraTMEFDataForCell(x, y)
        val shortForecast: List<ShortForecastResponseDTO> = shortGridForecastService.getAllShortTMEFDataForCell(x, y)

        // 3) 중기 예보 필터링
        val midCombinedForecast = midCombinedForecastService.findAll().filter { it.siRegId == siRegId }

        // 4) 모든 예보를 DTO로 묶어서 반환
        return ForecastResponseDTO(
            ultraForecastResponse = ultraForecast,
            shortForecastResponse = shortForecast,
            midCombinedForecastDTO = midCombinedForecast
        )
    }

    // 관측 시각에 따라 예보 타입 판별
    fun getForecastType(observationTime: LocalDateTime): ForecastType {
        val now = LocalDateTime.now()
        val hours = ChronoUnit.HOURS.between(now, observationTime)
        return when {
            hours <= 6 -> ForecastType.ULTRA
            hours <= 72 -> ForecastType.SHORT
            else -> ForecastType.MID
        }
    }

    fun formatToForecastTMEF(time: LocalDateTime): String {
        return time.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
    }

    // 예보 타입에 따라 sky 점수를 계산 (1: 맑음, 4: 흐림)
    fun getSkyScoreByAllTypes(lat: Double, lon: Double, time: LocalDateTime): Double {
        val forecast = getForecastDataByLocation(lat, lon)
        val type = getForecastType(time)
        val tStr = formatToForecastTMEF(time)

        return when (type) {
            ForecastType.ULTRA -> {
                val ultra = forecast.ultraForecastResponse.find { it.tmef == tStr }
                when (ultra?.sky?.toInt()) {
                    1 -> 1.0; 2 -> 0.66; 3 -> 0.33; 4 -> 0.0
                    else -> 0.5
                }
            }
            ForecastType.SHORT -> {
                val short = forecast.shortForecastResponse.find { it.tmef == tStr }
                when (short?.sky?.toInt()) {
                    1 -> 1.0; 2 -> 0.66; 3 -> 0.33; 4 -> 0.0
                    else -> 0.5
                }
            }
            ForecastType.MID -> {
                val mid = forecast.midCombinedForecastDTO.find {
                    it.tmEf.startsWith(tStr.substring(0, 8)) // 중기는 날짜 기반
                }
                when (mid?.sky) {
                    "WB01" -> 1.0; "WB02" -> 0.66; "WB03" -> 0.33; "WB04" -> 0.0
                    else -> 0.5
                }
            }
        }
    }

    // 예보 타입에 따라 강수 점수를 계산 (0: 강수 없음 → 1.0점)
    fun getPreScoreByAllTypes(lat: Double, lon: Double, time: LocalDateTime): Double {
        val forecast = getForecastDataByLocation(lat, lon)
        val type = getForecastType(time)
        val tStr = formatToForecastTMEF(time)

        return when (type) {
            ForecastType.ULTRA -> {
                val ultra = forecast.ultraForecastResponse.find { it.tmef == tStr }
                if ((ultra?.pty ?: 0.0) == 0.0) 1.0 else 0.0
            }
            ForecastType.SHORT -> {
                val short = forecast.shortForecastResponse.find { it.tmef == tStr }
                if ((short?.pty ?: 0.0) == 0.0) 1.0 else 0.0
            }
            ForecastType.MID -> {
                val mid = forecast.midCombinedForecastDTO.find {
                    it.tmEf.startsWith(tStr.substring(0, 8))
                }
                if ((mid?.pre ?: "WB00") == "WB00") 1.0 else 0.0
            }
        }
    }

    // 예보 타입에 따라 풍속 점수를 계산 (풍속 낮을수록 점수 높음), 중기는 고려하지 않음
    fun getWindScoreByAllTypes(lat: Double, lon: Double, time: LocalDateTime): Double? {
        val forecast = getForecastDataByLocation(lat, lon)
        val type = getForecastType(time)
        val tStr = formatToForecastTMEF(time)

        val wsd = when (type) {
            ForecastType.ULTRA -> forecast.ultraForecastResponse.find { it.tmef == tStr }?.wsd
            ForecastType.SHORT -> forecast.shortForecastResponse.find { it.tmef == tStr }?.wsd
            ForecastType.MID -> null // 중기는 풍속 정보 없음
        }

        return wsd?.let {
            val decayFactor = 5.0 // 기준 풍속을 정해야함
            1.0 / (1.0 + (it / decayFactor)) // 풍속이 높을수록 점수는 줄어듦
        }
    }
}