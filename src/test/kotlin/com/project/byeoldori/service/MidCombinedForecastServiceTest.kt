package com.project.byeoldori.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.entity.MidForecast
import com.project.byeoldori.forecast.entity.MidTempForecast
import com.project.byeoldori.forecast.repository.MidCombinedForecastRepository
import com.project.byeoldori.forecast.service.MidCombinedForecastService
import com.project.byeoldori.forecast.utils.forecasts.MidForecastParser
import com.project.byeoldori.forecast.utils.forecasts.MidTempForecastParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class MidCombinedForecastServiceTest {

    @Mock lateinit var midForecastParser: MidForecastParser
    @Mock lateinit var midTempForecastParser: MidTempForecastParser
    @Mock lateinit var combinedForecastRepository: MidCombinedForecastRepository
    @Mock lateinit var weatherData: WeatherData

    private val service by lazy {
        MidCombinedForecastService(
            midForecastParser, midTempForecastParser, combinedForecastRepository, weatherData
        )
    }

    @Test
    fun `육상예보와 기온예보가 siRegId로 정상 병합된다`() {
        // 경기도(11B00000) 육상 예보
        val land = listOf(
            MidForecast(tmFc = "202604061800", tmEf = "202604090000", regId = "11B00000", sky = "맑음", pre = "없음", rnSt = 10)
        )
        // 경기도 소속 시 코드(11B10101) 기온 예보
        val temp = listOf(
            MidTempForecast(tmFc = "202604061800", tmEf = "202604090000", regId = "11B10101", min = 5, max = 18)
        )

        val result = service.mergeForecasts(land, temp)

        val merged = result.firstOrNull { it.siRegId == "11B10101" }
        assertNotNull(merged, "11B10101 시 예보가 병합되어야 합니다")
        assertEquals("맑음", merged?.sky)
        assertEquals(5, merged?.min)
        assertEquals(18, merged?.max)
    }

    @Test
    fun `기온 데이터 없는 시의 min max는 null이다`() {
        val land = listOf(
            MidForecast(tmFc = "202604061800", tmEf = "202604090000", regId = "11B00000", sky = "구름많음", pre = "없음", rnSt = 20)
        )
        // 기온 예보 없음
        val result = service.mergeForecasts(land, emptyList())

        // 경기도 소속 시들은 min/max가 null이어야 함
        assertTrue(result.all { it.min == null && it.max == null }, "기온 데이터 없으면 min/max는 null이어야 함")
    }

    @Test
    fun `알 수 없는 도 코드는 병합 결과에서 제외된다`() {
        val land = listOf(
            MidForecast(tmFc = "202604061800", tmEf = "202604090000", regId = "UNKNOWN_DO", sky = "맑음", pre = "없음", rnSt = 0)
        )
        val result = service.mergeForecasts(land, emptyList())

        assertTrue(result.isEmpty(), "알 수 없는 도 코드는 결과에서 제외되어야 함")
    }

    @Test
    fun `00시가 아닌 기온 예보는 병합에 사용되지 않는다`() {
        val land = listOf(
            MidForecast(tmFc = "202604061800", tmEf = "202604090000", regId = "11B00000", sky = "맑음", pre = "없음", rnSt = 10)
        )
        // tmEf가 1200 → 00시가 아님
        val temp = listOf(
            MidTempForecast(tmFc = "202604061800", tmEf = "202604091200", regId = "11B10101", min = 5, max = 18)
        )

        val result = service.mergeForecasts(land, temp)
        val merged = result.firstOrNull { it.siRegId == "11B10101" }

        assertNotNull(merged)
        assertNull(merged?.min, "00시가 아닌 기온 데이터는 사용되지 않아야 함")
    }
}
