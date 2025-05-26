package com.project.byeoldori.forecast.controller

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.*
import com.project.byeoldori.forecast.service.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/weather")
class WeatherController(
    private val ultraForecastService: UltraGridForecastService,
    private val shortForecastService: ShortGridForecastService,
    private val foreCastService: ForeCastService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/UltraForecastCellData")
    fun getUltraForecastCellData(
        @RequestParam x: Int,
        @RequestParam y: Int
    ): List<UltraForecastResponseDTO> {
        logger.info("x좌표 $x Y좌표 $y")
        return ultraForecastService.getAllUltraTMEFDataForCell(x, y)
    }

    @GetMapping("/ShortForecastCellData")
    fun getShortForecastCellData(
        @RequestParam x: Int,
        @RequestParam y: Int
    ): List<ShortForecastResponseDTO> {
        logger.info("x좌표 $x Y좌표 $y")
        return shortForecastService.getAllShortTMEFDataForCell(x, y)
    }

    @GetMapping("/ForecastData")
    fun getForecastData(
        @RequestParam lat: Double,
        @RequestParam long: Double,
    ): ForecastResponseDTO {
        logger.info("ForecastData 호출 lat $lat, long $long")
        return foreCastService.getForecastDataByLocation(lat, long)
    }
}