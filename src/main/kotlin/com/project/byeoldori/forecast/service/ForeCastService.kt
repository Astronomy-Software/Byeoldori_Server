package com.project.byeoldori.forecast.service

import com.project.byeoldori.common.web.OutOfServiceAreaException
import com.project.byeoldori.forecast.dto.*
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

        // 3) 중기 예보 필터링
        val midCombinedForecast = midCombinedForecastService.findAll().filter { it.siRegId == siRegId }

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
}