package com.project.byeoldori.forecast.config

import com.project.byeoldori.forecast.service.UltraGridForecastService
import com.project.byeoldori.forecast.service.ShortGridForecastService
import com.project.byeoldori.forecast.service.MidCombinedForecastService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component("weatherApi")
class WeatherApiHealthIndicator(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val midCombinedForecastService: MidCombinedForecastService
) : HealthIndicator {

    override fun health(): Health {
        val ultraCount = ultraGridForecastService.getDataCount()
        val shortCount = shortGridForecastService.getDataCount()
        val midCount   = midCombinedForecastService.count()

        val builder = if (ultraCount > 0 && shortCount > 0) Health.up() else Health.down()

        return builder
            .withDetail("ultra_forecast_slots", ultraCount)
            .withDetail("short_forecast_slots", shortCount)
            .withDetail("mid_forecast_rows",    midCount)
            .build()
    }
}
