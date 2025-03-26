package com.project.byeoldori.service

import com.project.byeoldori.dto.ForecastResponseDTO
import com.project.byeoldori.dto.MidForecastResponseDTO
import com.project.byeoldori.dto.UltraForecastResponseDTO
import com.project.byeoldori.dto.ShortForecastResponseDTO
import latLonToGrid
import org.springframework.stereotype.Service

@Service
class ForeCastService(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val midForecastService: MidForecastService
) {
    fun getForecastDataByLocation(latitude: Double, longitude: Double): ForecastResponseDTO {
        // 1) 위경도 -> 격자 좌표 변환
        val (x, y) = latLonToGrid(latitude, longitude)

        val ultraForecast: List<UltraForecastResponseDTO> = ultraGridForecastService.getAllUltraTMEFDataForCell(x, y)
        val shortForecast: List<ShortForecastResponseDTO> = shortGridForecastService.getAllShortTMEFDataForCell(x, y)

        // 3) midForecastResponseDTO 필드에 임시 값 넣기
        //    MidForecastResponseDTO 생성자에 맞춰 더미 데이터를 넣습니다.
        //    예: 만약 MidForecastResponseDTO가 (val date: String, val comment: String) 이런 식이라면:
        val midForecastDummyList = listOf(
            MidForecastResponseDTO(
                regId = "REG001",
                tmFc = "2025-03-26 12:00",
                tmEf = "2025-03-27 00:00",
                modCode = "MOD123",
                stn = "STN001",
                c = "C0001",
                sky = "맑음",
                pre = "0.0mm",
                conf = "높음",
                wf = "맑은 날씨",
                rnSt = 0
            ),
            MidForecastResponseDTO(
                regId = "REG002",
                tmFc = "2025-03-26 18:00",
                tmEf = "2025-03-27 06:00",
                modCode = "MOD456",
                stn = "STN002",
                c = "C0002",
                sky = "흐림",
                pre = "1.2mm",
                conf = "보통",
                wf = "구름 많고 비 소량",
                rnSt = 40
            )
        )


        // 4) 최종적으로 ForecastResponseDTO 반환
        return ForecastResponseDTO(
            ultraForecastResponse = ultraForecast,
            shortForecastResponse = shortForecast,
            midForecastResponseDTO = midForecastDummyList
        )
    }
}
