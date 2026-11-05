package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.KmaReachability
import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotStore
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotTest
import com.project.byeoldori.forecast.utils.forecasts.ForecastElement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono

/**
 * 초단기·단기도 한 tmef 의 요소 하나가 비면(0행) 그 tmef 만 건너뛰고 나머지 tmef 는 반영한다.
 * 실황(#179)과 같은 크기 검사를 결합 전에 해서, 인덱스 예외 대신 이유가 담긴 실패로 처리한다.
 */
class TmefIncompleteGridTest {

    private val full = (0 until 149 * 2).joinToString(",") { "3.0" }
    private val badTmef = "202611060300"
    private val ultraTmefs = (0 until 6).map { "2026110603%02d".format(it * 10) }
    private val shortTmefs = listOf("2026110603", "2026110604", "2026110605")

    private val weatherData: WeatherData = mock(WeatherData::class.java).also {
        `when`(it.fetchUltraShortForecast(anyString(), anyString(), anyString())).thenAnswer { inv ->
            val tmef = inv.getArgument<String>(1); val vars = inv.getArgument<String>(2)
            Mono.just(if (tmef == badTmef && vars == ForecastElement.VEC) "" else full)
        }
        `when`(it.fetchShortForecast(anyString(), anyString(), anyString())).thenAnswer { inv ->
            val tmef = inv.getArgument<String>(1); val vars = inv.getArgument<String>(2)
            Mono.just(if (tmef == shortTmefs[1] && vars == ForecastElement.SNO) "" else full)
        }
    }
    private fun store() = ForecastSnapshotStore(ForecastSnapshotTest.MemoryBlobStore())

    @Test
    fun `초단기 한 tmef 의 요소가 비면 그 tmef 만 빠지고 주기는 성공한다`() {
        val ultra = UltraGridForecastService(weatherData, store(), KmaReachability())

        val error = catchThrowable { ultra.updateAllUltraTMEFData("202611060230", ultraTmefs).block() }

        assertThat(error).isNull()
        assertThat(ultra.getDataCount()).isEqualTo(ultraTmefs.size - 1)
    }

    @Test
    fun `단기 한 tmef 의 요소가 비면 그 tmef 만 빠지고 주기는 성공한다`() {
        val short = ShortGridForecastService(weatherData, store(), KmaReachability())

        val error = catchThrowable { short.updateAllShortTMEFData("2026110602", shortTmefs).block() }

        assertThat(error).isNull()
        assertThat(short.getDataCount()).isEqualTo(shortTmefs.size - 1)
    }
}
