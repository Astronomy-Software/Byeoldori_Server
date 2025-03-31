package com.project.byeoldori.service

import com.project.byeoldori.dto.ForecastResponseDTO
import com.project.byeoldori.dto.UltraForecastResponseDTO
import com.project.byeoldori.dto.ShortForecastResponseDTO
import com.project.byeoldori.region.RegionMapper
import latLonToGrid
import org.springframework.stereotype.Service

@Service
class ForeCastService(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val midCombinedForecastService: MidCombinedForecastService
) {
    fun getForecastDataByLocation(latitude: Double, longitude: Double): ForecastResponseDTO {

        // 1) 위경도 -> 격자x 좌표 변환
        val (x, y) = latLonToGrid(latitude, longitude)
        val siRegId = getNearestSiRegIdByLocation(latitude, longitude) // 위경도로 매핑, 임시 방식 적용
        // val siRegId = RegionMapper.getSiByGrid(x, y) ?: "UNKNOWN" 이건 격자 좌표로 매핑, 좌표와 시지역코드 매핑 시켜줘야함
        val doRegId = RegionMapper.getDoBySi(siRegId) ?: "UNKNOWN"

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

    // 임시 매핑 함수로 위도,경도 -> 시 코드 변환
    private fun getNearestSiRegIdByLocation(lat: Double, lon: Double): String {
        return when {
            lat in 37.4..37.6 && lon in 126.9..127.1 -> "11B10101" // 서울 종로구
            lat in 36.6..36.7 && lon in 127.3..127.5 -> "11C10301" // 대전 유성구
            else -> "11B10101" // 기본값: 서울
        }
    }
}