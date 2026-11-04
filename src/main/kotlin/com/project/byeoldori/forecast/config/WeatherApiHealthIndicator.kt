package com.project.byeoldori.forecast.config

import com.project.byeoldori.forecast.service.UltraGridForecastService
import com.project.byeoldori.forecast.service.ShortGridForecastService
import com.project.byeoldori.forecast.service.MidCombinedForecastService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
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

        // 초단기·단기 예보는 컨테이너 메모리에 있어, 새로 뜬 색은 기동 직후 비어 있다.
        // 적재 전에는 WARMING 을 낸다(Redis 스냅샷에서 올린 경우도 적재로 보아 바로 UP). 기본 health 의 상태 순서(DOWN, OUT_OF_SERVICE, UP, UNKNOWN)에 없는
        // 상태라 /actuator/health 집계에서는 무시되고(docker healthcheck·업타임 체크는 그대로 UP),
        // warm 그룹(/actuator/health/warm)에서만 503 으로 드러나 블루그린 배포가 적재를 기다린 뒤 전환한다.
        val loaded = ultraCount > 0 && shortCount > 0
        return Health.status(if (loaded) Status.UP else WARMING)
            .withDetail("ultra_forecast_slots", ultraCount)
            .withDetail("short_forecast_slots", shortCount)
            .withDetail("mid_forecast_rows",    midCount)
            .withDetail("ultra_loaded",  ultraCount > 0)
            .withDetail("short_loaded",  shortCount > 0)
            .build()
    }

    companion object {
        val WARMING = Status("WARMING", "초단기·단기 예보 메모리 캐시 적재 전")
    }
}
