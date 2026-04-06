package com.project.byeoldori.forecast.service

import com.project.byeoldori.common.web.OutOfServiceAreaException
import com.project.byeoldori.forecast.dto.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.project.byeoldori.forecast.utils.region.GeoBounds
import com.project.byeoldori.forecast.utils.region.RegionMapper
import com.project.byeoldori.forecast.utils.score.WeatherScoreCalculator
import com.project.byeoldori.forecast.utils.score.LightPollution
import latLonToGrid
import org.springframework.stereotype.Service

@Service
class ForeCastService(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val midCombinedForecastService: MidCombinedForecastService,
    private val weatherScoreCalculator: WeatherScoreCalculator,
    private val lightPollution: LightPollution
) {
    fun getForecastDataByLocation(latitude: Double, longitude: Double): ForecastResponseDTO {
        if (!GeoBounds.isInKorea(latitude, longitude)) {
            throw OutOfServiceAreaException()
        }

        // 1) 위경도 -> 격자x 좌표 변환
        val (x, y) = latLonToGrid(latitude, longitude)

        // 1-2) 위경도 -> 좌표로 변환 후 시 지역 코드로 매핑
        val siRegId = RegionMapper.getSiByLatLon(latitude, longitude) ?: "UNKNOWN"

        // 2) 초단기, 단기 예보
        val ultraForecast: List<UltraForecastResponseDTO> = ultraGridForecastService.getAllUltraTMEFDataForCell(x, y)
        val shortForecast: List<ShortForecastResponseDTO> = shortGridForecastService.getAllShortTMEFDataForCell(x, y)

        // 3) 중기 예보 조회 (siRegId 기준으로 DB에서 직접 조회)
        val midCombinedForecast = midCombinedForecastService.findBySiRegId(siRegId)

        // 4) 광공해 점수
        val lightScore = lightPollution.getLightPollutionScore(latitude, longitude)

        // 5) 예보별 관측적합도
        val ultraScored = ultraForecast.map { u ->
            val s = weatherScoreCalculator.suitabilityForUltra(u, lightScore)
            u.copy(suitability = s.total) }
        val shortScored = shortForecast.map { srt ->
            val s = weatherScoreCalculator.suitabilityForShort(srt, lightScore, latitude, longitude)
            srt.copy(suitability = s.total) }
        val midScored   = midCombinedForecast.map { m ->
            val s = weatherScoreCalculator.suitabilityForMid(m, lightScore, latitude, longitude)
            m.copy(suitability = s.total) }

        return ForecastResponseDTO(ultraScored, shortScored, midScored)
    }

    fun getWeatherSummary(latitude: Double, longitude: Double): WeatherSummaryDto {
        val full = getForecastDataByLocation(latitude, longitude)
        val fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        val now = LocalDateTime.now()
        val nowStr = now.format(fmt)

        // 현재 이후 단기 예보 중 가장 가까운 시간대
        val current = full.shortForecastResponse
            .filter { it.tmef >= nowStr }
            .minByOrNull { it.tmef }
            ?: full.ultraForecastResponse
                .filter { it.tmef >= nowStr }
                .minByOrNull { it.tmef }
                ?.let { u ->
                    ShortForecastResponseDTO(
                        tmef = u.tmef, tmp = u.t1h, tmx = null, tmn = null,
                        vec = u.vec?.toFloat(), wsd = u.wsd, sky = u.sky,
                        pty = u.pty, pop = null, pcp = null, sno = null, reh = u.reh,
                        suitability = u.suitability
                    )
                }

        val skyText = when (current?.sky) {
            1 -> "맑음"; 2 -> "구름조금"; 3 -> "구름많음"; 4 -> "흐림"; else -> "정보없음"
        }

        // 다음 관측 적합 시각: 단기 또는 초단기에서 suitability >= 70 인 첫 번째 미래 시각
        val goodShort = full.shortForecastResponse
            .filter { it.tmef > nowStr && it.suitability >= 70 }
            .minByOrNull { it.tmef }?.tmef
        val goodUltra = full.ultraForecastResponse
            .filter { it.tmef > nowStr && it.suitability >= 70 }
            .minByOrNull { it.tmef }?.tmef
        val nextGoodTime = when {
            goodUltra != null && (goodShort == null || goodUltra < goodShort) -> goodUltra
            else -> goodShort
        }

        return WeatherSummaryDto(
            suitability = current?.suitability ?: 0,
            sky = skyText,
            temperature = current?.tmp,
            nextGoodTime = nextGoodTime
        )
    }
}