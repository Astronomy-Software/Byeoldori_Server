package com.project.byeoldori.forecast.controller

import com.project.byeoldori.forecast.dto.*
import com.project.byeoldori.forecast.service.*
import org.slf4j.LoggerFactory
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/weather")
class WeatherController(
    private val foreCastService: ForeCastService
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/ForecastData")
    fun getForecastData(
        @RequestParam("lat") lat: Double,
        @RequestParam(name = "lon", required = false) lon: Double?
    ): ForecastResponseDTO {
        logger.info("ForecastData 호출 lat $lat, long $lon")

        val longitude = lon
        ?: throw MissingServletRequestParameterException("lon", "Double")

        return foreCastService.getForecastDataByLocation(lat, longitude)
    }
}