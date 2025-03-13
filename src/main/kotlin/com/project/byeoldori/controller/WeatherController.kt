package com.project.byeoldori.controller

import com.project.byeoldori.api.WeatherData
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class WeatherController(
    private val weatherData: WeatherData
) {
    @GetMapping("/weather/live")
    fun getLiveWeather(
        @RequestParam tmfc: String,
        @RequestParam tmef: String,
        @RequestParam vars: String
    ): Mono<String> {
        return weatherData.fetchLiveWeather(tmfc, tmef, vars)
    }

    @GetMapping("/weather/ultra-short-term")
    fun getUltraShortTermForecast(
        @RequestParam tmfc: String,
        @RequestParam tmef: String,
        @RequestParam vars: String
    ): Mono<String> {
        return weatherData.fetchUltraShortForecast(tmfc, tmef, vars)
    }

    @GetMapping("/weather/short-term")
    fun getShortTermForecast(
        @RequestParam tmfc: String,
        @RequestParam tmef: String,
        @RequestParam vars: String
    ): Mono<String> {
        return weatherData.fetchShortForecast(tmfc, tmef, vars)
    }

    @GetMapping("/weather/mid-land")
    fun getMidLandForecast(): Mono<String> {
        return weatherData.fetchMidLandForecast()
    }

    @GetMapping("/weather/mid-temp")
    fun getMidTemperatureForecast(): Mono<String> {
        return weatherData.fetchMidTemperatureForecast()
    }
}
