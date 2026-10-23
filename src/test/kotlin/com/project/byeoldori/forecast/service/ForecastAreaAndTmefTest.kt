package com.project.byeoldori.forecast.service

import com.project.byeoldori.common.web.OutOfServiceAreaException
import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotStore
import com.project.byeoldori.forecast.utils.score.LightPollution
import com.project.byeoldori.forecast.utils.score.WeatherScoreCalculator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * (1) 서비스 영역 박스 안이지만 기상청 격자 밖인 좌표가 500 이 아니라 서비스 지역 밖(418)으로 응답되는지,
 * (2) 단기예보 tmef 가 초단기와 같은 12자리로 응답되는지 확인한다.
 */
class ForecastAreaAndTmefTest {

    private val ultra = mock(UltraGridForecastService::class.java)
    private val short = mock(ShortGridForecastService::class.java)
    private val live = mock(LiveGridForecastService::class.java)
    private val service = ForeCastService(
        ultra, short, live,
        mock(MidCombinedForecastService::class.java),
        mock(WeatherScoreCalculator::class.java),
        mock(LightPollution::class.java),
    )

    @Test
    fun `GeoBounds 안이지만 격자 밖인 남동쪽 모서리는 OutOfServiceAreaException`() {
        for ((lat, lon) in listOf(33.0 to 132.0, 34.0 to 131.9)) {
            assertThatThrownBy { service.getLiveWeather(lat, lon) }
                .`as`("lat=%s lon=%s", lat, lon)
                .isInstanceOf(OutOfServiceAreaException::class.java)
            assertThatThrownBy { service.getForecastDataByLocation(lat, lon) }
                .isInstanceOf(OutOfServiceAreaException::class.java)
        }
        verify(live, never()).getLiveDataForCell(anyInt(), anyInt())
        verify(short, never()).getAllShortTMEFDataForCell(anyInt(), anyInt())
    }

    @Test
    fun `박스 밖 좌표는 기존처럼 OutOfServiceAreaException`() {
        assertThatThrownBy { service.getLiveWeather(35.6, 139.7) }
            .isInstanceOf(OutOfServiceAreaException::class.java)
    }

    @Test
    fun `단기예보 tmef 는 12자리(yyyyMMddHHmm)로 응답한다`() {
        val svc = ShortGridForecastService(mock(WeatherData::class.java), mock(ForecastSnapshotStore::class.java))
        val cell = ShortGridCell(tmp = 12, tmx = null, tmn = null, vec = null, wsd = null, sky = 1,
            pty = 0, pop = 0, pcp = null, sno = null, reh = 50)
        val grid = mutableListOf(mutableListOf(cell))
        val field = ShortGridForecastService::class.java.getDeclaredField("shortTMEFGridMap")
        field.isAccessible = true
        field.set(svc, linkedMapOf("2026102021" to grid, "2026102022" to grid))

        val res = svc.getAllShortTMEFDataForCell(0, 0)
        assertThat(res.map { it.tmef }).containsExactly("202610202100", "202610202200")
    }
}
