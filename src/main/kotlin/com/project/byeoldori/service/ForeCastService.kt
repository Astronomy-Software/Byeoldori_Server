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
    private val midForecastService: MidForecastService,
    private val midTempForecastService: MidTempForecastService,
    private val midCombinedForecastService: MidCombinedForecastService
) {
    fun getForecastDataByLocation(latitude: Double, longitude: Double): ForecastResponseDTO {
        //TODO : midforecast 정보 반환하는것 추가하고 더미데이터 삭제

        // 1) 위경도 -> 격자x 좌표 변환
        val (x, y) = latLonToGrid(latitude, longitude)

        val ultraForecast: List<UltraForecastResponseDTO> = ultraGridForecastService.getAllUltraTMEFDataForCell(x, y)
        val shortForecast: List<ShortForecastResponseDTO> = shortGridForecastService.getAllShortTMEFDataForCell(x, y)

        // 3) 시/도 코드 추정
        val siRegId = getNearestSiRegIdByLocation(latitude, longitude)
        val doRegId = RegionMapper.getDoBySi(siRegId) ?: "UNKNOWN"

        // 4) 중기 예보 필터링
        val midForecast = midForecastService.findAll().filter { it.regId == doRegId }
        val midTempForecast = midTempForecastService.findAll().filter { it.regId == siRegId }
        val midCombinedForecast = midCombinedForecastService.findAll().filter { it.siRegId == siRegId }

        // 5) 모든 예보를 DTO로 묶어서 반환
        return ForecastResponseDTO(
            ultraForecastResponse = ultraForecast,
            shortForecastResponse = shortForecast,
            midForecastResponseDTO = midForecast,
            midTempForecastResponseDTO = midTempForecast,
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