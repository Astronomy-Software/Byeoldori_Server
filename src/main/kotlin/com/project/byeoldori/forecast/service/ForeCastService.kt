package com.project.byeoldori.forecast.service

import com.project.byeoldori.common.web.OutOfServiceAreaException
import com.project.byeoldori.forecast.dto.*
import com.project.byeoldori.forecast.utils.region.GeoBounds
import com.project.byeoldori.forecast.utils.region.RegionMapper
import latLonToGrid
import org.springframework.stereotype.Service

@Service
class ForeCastService(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val midCombinedForecastService: MidCombinedForecastService
) {
    fun getForecastDataByLocation(latitude: Double, longitude: Double): ForecastResponseDTO {

        if (!GeoBounds.isInKorea(latitude, longitude)) {
            val msg = "한국 내 좌표만 지원합니다. (위도: ${GeoBounds.LAT_MIN}~${GeoBounds.LAT_MAX}, 경도: ${GeoBounds.LON_MIN}~${GeoBounds.LON_MAX})"
            throw OutOfServiceAreaException(msg)
        }

        // 1) 위경도 -> 격자x 좌표 변환
        val (x, y) = latLonToGrid(latitude, longitude)

        // 1-2) 위경도 -> 좌표로 변환 후 시 지역 코드로 매핑
        val siRegId = RegionMapper.getSiByLatLon(latitude, longitude) ?: "UNKNOWN"

        // 2) 초단기, 단기 예보
        val ultraForecast: List<UltraForecastResponseDTO> = ultraGridForecastService.getAllUltraTMEFDataForCell(x, y)
        val shortForecastRaw: List<ShortForecastResponseDTO> = shortGridForecastService.getAllShortTMEFDataForCell(x, y)
        val shortForecastProcessed = fillMissingShortTermTemperatures(shortForecastRaw)

        // 3) 중기 예보 필터링
        val midCombinedForecast = midCombinedForecastService.findAll().filter { it.siRegId == siRegId }

        // 4) 모든 예보를 DTO로 묶어서 반환
        return ForecastResponseDTO(
            ultraForecastResponse = ultraForecast,
            shortForecastResponse = shortForecastProcessed,
            midCombinedForecastDTO = midCombinedForecast
        )
    }

    private fun fillMissingShortTermTemperatures(forecasts: List<ShortForecastResponseDTO>): List<ShortForecastResponseDTO> {
        return forecasts.groupBy { it.tmef.substring(0, 8) }
            .flatMap { (_, dailyForecasts) ->

                val validTmn = dailyForecasts.map { it.tmn }.firstOrNull { it != -999 && it != null }
                val validTmx = dailyForecasts.map { it.tmx }.firstOrNull { it != -999 && it != null }

                if (validTmn != null || validTmx != null) {
                    dailyForecasts.map { forecast ->
                        forecast.copy(
                            tmn = validTmn ?: forecast.tmn,
                            tmx = validTmx ?: forecast.tmx
                        )
                    }
                } else {
                    dailyForecasts
                }
            }
            .sortedBy { it.tmef }
    }
}