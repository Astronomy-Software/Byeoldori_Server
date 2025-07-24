package com.project.byeoldori.forecast.controller

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
        @RequestParam long: Double,
    ): ForecastResponseDTO {
        logger.info("ForecastData 호출 lat $lat, long $long")
        return foreCastService.getForecastDataByLocation(lat, long)
    }

    @Operation(summary = "중기 육상 + 기온 예보 조회", description = "기상청 중기 육상 + 예보 데이터를 호출 후 DB에 저장합니다.")
    @PostMapping("/mid-combined")
    fun fetchAndSaveMidCombinedForecastFromApi(): ResponseEntity<String> {
        val result = midCombinedForecastService.fetchAndSaveFromApi()
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "저장된 중기 예보 전체 조회", description = "DB에 저장된 병합된 중기 육상 및 기온 데이터를 모두 조회합니다.")
    @GetMapping("/mid-combined/all")
    fun getAllSavedForecasts(): ResponseEntity<List<MidCombinedForecastDTO>> {
        val dtoList = midCombinedForecastService.findAll()
        return ResponseEntity.ok(dtoList)
    }
}