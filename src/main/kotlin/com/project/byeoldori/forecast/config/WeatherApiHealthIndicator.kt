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

        // 날씨 데이터 적재 여부는 정보성 — 배포 헬스체크에 영향 주지 않음
        return Health.up()
            .withDetail("ultra_forecast_slots", ultraCount)
            .withDetail("short_forecast_slots", shortCount)
            .withDetail("mid_forecast_rows",    midCount)
            .withDetail("ultra_loaded",  ultraCount > 0)
            .withDetail("short_loaded",  shortCount > 0)
            .build()
    }
}
