package com.project.byeoldori.controller

import com.project.byeoldori.api.WeatherData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/weather")
class WeatherController(
    private val weatherData: WeatherData
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

    @Operation(summary = "초단기 예보 조회", description = "기상청 초단기 예보 데이터를 호출 합니다.")
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

    @Operation(summary = "중기 육상 예보 조회", description = "기상청 중기 육상 예보 데이터를 호출합니다.")
    @GetMapping("/mid-land")
    fun getMidLandForecast(): Mono<ResponseEntity<String>> {
        return weatherData.fetchMidLandForecast()
            .map { ResponseEntity.ok(it) }
    }

    @Operation(summary = "중기 기온 예보 조회", description = "기상청 중기 기온 예보 데이터를 호출합니다.")
    @GetMapping("/mid-temp")
    fun getMidTemperatureForecast(): Mono<ResponseEntity<String>> {
        return weatherData.fetchMidTemperatureForecast()
            .map { ResponseEntity.ok(it) }
    }
}