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
        @RequestParam lon: Double,
    ): ForecastResponseDTO {
        logger.info("ForecastData 호출 lat $lat, lon $lon")
        return foreCastService.getForecastDataByLocation(lat, lon)
    }
}