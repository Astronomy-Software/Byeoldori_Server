package com.project.byeoldori.forecast.controller

import com.project.byeoldori.forecast.dto.*
import com.project.byeoldori.forecast.service.*
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/weather")
class WeatherController(
    private val foreCastService: ForeCastService
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
}