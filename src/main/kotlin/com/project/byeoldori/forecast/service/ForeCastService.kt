package com.project.byeoldori.forecast.service

import com.project.byeoldori.common.web.OutOfServiceAreaException
import com.project.byeoldori.forecast.dto.ForecastResponseDTO
import com.project.byeoldori.forecast.dto.UltraForecastResponseDTO
import com.project.byeoldori.forecast.dto.ShortForecastResponseDTO
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
            throw OutOfServiceAreaException(null)
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

        // 4) 모든 예보를 DTO로 묶어서 반환
        return ForecastResponseDTO(
            ultraForecastResponse = ultraForecast,
            shortForecastResponse = shortForecast,
            midCombinedForecastDTO = midCombinedForecast
        )
    }
}