package com.project.byeoldori.controller

import com.project.byeoldori.api.WeatherData
import com.project.byeoldori.dto.MidForecastResponseDTO
import com.project.byeoldori.service.MidForecastService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/weather")
class WeatherController(
    private val weatherData: WeatherData,
    private val midForecastService: MidForecastService
) {

    @Operation(summary = "실시간 날씨 조회", description = "기상청 실황 날씨 데이터를 호출합니다.")
    @GetMapping("/live")
    fun getLiveWeather(
        @Parameter(description = "발표시간 (tmfc)") @RequestParam tmfc: String,
        @Parameter(description = "조회할 날씨 데이터 (vars)") @RequestParam vars: String
    ): Mono<ResponseEntity<String>> {
        return weatherData.fetchLiveWeather(tmfc, vars)
            .map { ResponseEntity.ok(it) }
    }

    @Operation(summary = "초단기 예보 조회", description = "기상청 초단기 예보 데이터를 호출합니다.")
    @GetMapping("/ultra-short-term")
    fun getUltraShortTermForecast(
        @Parameter(description = "발표시간 (tmfc)") @RequestParam tmfc: String,
        @Parameter(description = "발효시간 (tmef)") @RequestParam tmef: String,
        @Parameter(description = "조회할 날씨 데이터 (vars)") @RequestParam vars: String
    ): Mono<ResponseEntity<String>> {
        return weatherData.fetchUltraShortForecast(tmfc, tmef, vars)
            .map { ResponseEntity.ok(it) }
    }

    @Operation(summary = "단기 예보 조회", description = "기상청 단기 예보 데이터를 호출합니다.")
    @GetMapping("/short-term")
    fun getShortTermForecast(
        @Parameter(description = "발표시간 (tmfc)") @RequestParam tmfc: String,
        @Parameter(description = "발효시간 (tmef)") @RequestParam tmef: String,
        @Parameter(description = "조회할 날씨 데이터 (vars)") @RequestParam vars: String
    ): Mono<ResponseEntity<String>> {
        return weatherData.fetchShortForecast(tmfc, tmef, vars)
            .map { ResponseEntity.ok(it) }
    }

    @Operation(summary = "중기 육상 예보 조회 및 저장", description = "기상청 중기 육상 예보 데이터를 호출 후 DB에 저장하고 응답합니다.")
    @GetMapping("/mid-land")
    fun getAndSaveMidLandForecast(): Mono<ResponseEntity<String>> {
        return midForecastService.fetchParseSaveAndRespond()
            .map { ResponseEntity.ok(it) } // 원본 데이터 응답
    }

    @Operation(summary = "중기 육상 예보 전체 조회", description = "DB에 저장된 중기 육상 예보 데이터를 모두 조회합니다.")
    @GetMapping("/mid-land/all")
    fun getAllMidLandForecasts(): ResponseEntity<List<MidForecastResponseDTO>> {
        val forecasts = midForecastService.findAll()
        return ResponseEntity.ok(forecasts)
    }

    @Operation(summary = "중기 기온 예보 조회", description = "기상청 중기 기온 예보 데이터를 호출합니다.")
    @GetMapping("/mid-temp")
    fun getMidTemperatureForecast(): Mono<ResponseEntity<String>> {
        return weatherData.fetchMidTemperatureForecast()
            .map { ResponseEntity.ok(it) }
    }
}