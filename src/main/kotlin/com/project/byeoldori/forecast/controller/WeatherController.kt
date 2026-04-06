package com.project.byeoldori.forecast.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.forecast.dto.*
import com.project.byeoldori.forecast.service.*
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/weather")
class WeatherController(
    private val foreCastService: ForeCastService,
    private val midCombinedForecastService: MidCombinedForecastService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/ForecastData")
    fun getForecastData(
        @RequestParam lat: Double,
        @RequestParam lon: Double,
    ): ForecastResponseDTO {
        logger.info("ForecastData 호출 lat $lat, lon $lon")
        return foreCastService.getForecastDataByLocation(lat, lon)
    }

    @Operation(
        summary = "날씨 예보 요약",
        description = "현재 관측 적합도, 하늘 상태, 기온, 다음 관측 적합 시각을 요약하여 반환합니다."
    )
    @GetMapping("/summary")
    fun getSummary(
        @RequestParam lat: Double,
        @RequestParam lon: Double
    ): ResponseEntity<ApiResponse<WeatherSummaryDto>> {
        logger.info("WeatherSummary 호출 lat={}, lon={}", lat, lon)
        return ResponseEntity.ok(ApiResponse.ok(foreCastService.getWeatherSummary(lat, lon)))
    }
}