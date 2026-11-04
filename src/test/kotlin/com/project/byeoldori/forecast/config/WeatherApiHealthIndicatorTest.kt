package com.project.byeoldori.forecast.config

import com.project.byeoldori.forecast.service.MidCombinedForecastService
import com.project.byeoldori.forecast.service.ShortGridForecastService
import com.project.byeoldori.forecast.service.UltraGridForecastService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.actuate.health.HealthEndpointGroups
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.SimpleHttpCodeStatusMapper
import org.springframework.boot.actuate.health.SimpleStatusAggregator
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.io.File
import java.util.Properties

/**
 * 배포 워밍업 게이트: 예보 캐시 적재 전에는 warm 그룹만 503 이 되고 기본 /actuator/health 는 UP 을 유지해야 한다.
 */
class WeatherApiHealthIndicatorTest {

    private val ultra: UltraGridForecastService = mock(UltraGridForecastService::class.java)
    private val short: ShortGridForecastService = mock(ShortGridForecastService::class.java)
    private val mid: MidCombinedForecastService = mock(MidCombinedForecastService::class.java)
    private val indicator = WeatherApiHealthIndicator(ultra, short, mid)

    private val props = Properties().apply {
        File("src/main/resources/application.properties").inputStream().use { load(it) }
    }

    private fun counts(ultraCount: Int, shortCount: Int) {
        `when`(ultra.getDataCount()).thenReturn(ultraCount)
        `when`(short.getDataCount()).thenReturn(shortCount)
        `when`(mid.count()).thenReturn(0L)
    }

    @Test
    fun `초단기와 단기 예보가 모두 적재되어야 UP 이다`() {
        counts(6, 0)
        assertThat(indicator.health().status).isEqualTo(WeatherApiHealthIndicator.WARMING)
        counts(0, 60)
        assertThat(indicator.health().status).isEqualTo(WeatherApiHealthIndicator.WARMING)
        counts(6, 60)
        assertThat(indicator.health().status).isEqualTo(Status.UP)
    }

    @Test
    fun `WARMING 은 기본 health 집계에서 무시되어 docker healthcheck 와 업타임 체크를 떨어뜨리지 않는다`() {
        val defaultAggregator = SimpleStatusAggregator()
        assertThat(defaultAggregator.getAggregateStatus(Status.UP, WeatherApiHealthIndicator.WARMING)).isEqualTo(Status.UP)
        assertThat(props.getProperty("management.endpoint.health.status.order")).isNull()
    }

    @Test
    fun `warm 그룹은 weatherApi 만 보고 적재 전에는 503 을 낸다`() {
        assertThat(props.getProperty("management.endpoint.health.group.warm.include")).isEqualTo("weatherApi")

        val order = props.getProperty("management.endpoint.health.group.warm.status.order").split(",")
        val warmAggregator = SimpleStatusAggregator(order)
        assertThat(warmAggregator.getAggregateStatus(WeatherApiHealthIndicator.WARMING)).isEqualTo(WeatherApiHealthIndicator.WARMING)
        assertThat(warmAggregator.getAggregateStatus(Status.UP)).isEqualTo(Status.UP)
        assertThat(warmAggregator.getAggregateStatus(Status.DOWN)).isEqualTo(Status.DOWN)

        val code = props.getProperty("management.endpoint.health.group.warm.status.http-mapping.WARMING").toInt()
        val mapper = SimpleHttpCodeStatusMapper(mapOf("WARMING" to code))
        assertThat(mapper.getStatusCode(WeatherApiHealthIndicator.WARMING)).isEqualTo(503)
        assertThat(mapper.getStatusCode(Status.UP)).isEqualTo(200)
    }

    @Test
    fun `실제 actuator 설정으로 적재 전에는 기본 health 가 UP, warm 그룹은 WARMING(503) 이다`() {
        val managementProps = props.stringPropertyNames()
            .filter { it.startsWith("management.") }
            .map { "$it=${props.getProperty(it)}" }
            .toTypedArray()
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(HealthContributorAutoConfiguration::class.java, HealthEndpointAutoConfiguration::class.java)
            )
            .withBean("weatherApi", HealthIndicator::class.java, { HealthIndicator { Health.status(WeatherApiHealthIndicator.WARMING).build() } })
            .withBean("db", HealthIndicator::class.java, { HealthIndicator { Health.up().build() } })
            .withPropertyValues(*managementProps)
            .run { ctx ->
                val endpoint = ctx.getBean(HealthEndpoint::class.java)
                assertThat(endpoint.health().status).isEqualTo(Status.UP)
                assertThat(endpoint.healthForPath("warm").status).isEqualTo(WeatherApiHealthIndicator.WARMING)
                val warm = ctx.getBean(HealthEndpointGroups::class.java).get("warm")
                assertThat(warm.httpCodeStatusMapper.getStatusCode(WeatherApiHealthIndicator.WARMING)).isEqualTo(503)
                assertThat(warm.isMember("db")).isFalse()
            }
    }
}
