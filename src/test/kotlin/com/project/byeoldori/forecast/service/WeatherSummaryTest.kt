package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.dto.ShortForecastResponseDTO
import com.project.byeoldori.forecast.dto.UltraForecastResponseDTO
import com.project.byeoldori.forecast.utils.score.AstroCalculator
import com.project.byeoldori.forecast.utils.score.LightPollution
import com.project.byeoldori.forecast.utils.score.WeatherScoreCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 홈 화면 요약(getWeatherSummary): 현재 이후 가장 가까운 예보의 하늘 상태·기온·적합도와
 * 적합도 70 이상인 첫 미래 시각(nextGoodTime)을 고른다. 점수는 실제 계산기로 매긴다.
 */
class WeatherSummaryTest {

    private val ultra = mock(UltraGridForecastService::class.java)
    private val short = mock(ShortGridForecastService::class.java)
    private val service = ForeCastService(
        ultra, short, mock(LiveGridForecastService::class.java), mock(MidCombinedForecastService::class.java),
        WeatherScoreCalculator(AstroCalculator()), mock(LightPollution::class.java),
    )

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    private val lat = 37.5665
    private val lon = 126.9780
    private val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    private fun t(hoursFromNow: Long) = now.plusHours(hoursFromNow).format(fmt)

    // 맑음·무강수·미풍·건조 → 90점 이상, 흐림·강수 → 30점 미만
    private fun short(tmef: String, clear: Boolean, tmp: Int = 12) = ShortForecastResponseDTO(
        tmef = tmef, tmp = tmp, tmx = null, tmn = null, vec = null, wsd = 1.5f,
        sky = if (clear) 1 else 4, pty = if (clear) 0 else 1, pop = if (clear) 0 else 90, pcp = if (clear) null else 3f,
        sno = null, reh = if (clear) 40 else 95,
    )

    private fun ultra(tmef: String, clear: Boolean, t1h: Int = 8) = UltraForecastResponseDTO(
        tmef = tmef, t1h = t1h, vec = 180, wsd = 1.5f, pty = if (clear) 0 else 1, rn1 = if (clear) 0f else 2f,
        reh = if (clear) 40 else 95, sky = if (clear) 1 else 4,
    )

    @Test
    fun `현재 이후 가장 가까운 단기 예보가 현재 상태가 된다`() {
        `when`(short.getAllShortTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(
            short(t(-2), clear = true, tmp = 99),     // 과거 → 무시
            short(t(3), clear = false, tmp = 20),
            short(t(1), clear = true, tmp = 11),      // 가장 가까운 미래
        ))

        val s = service.getWeatherSummary(lat, lon)

        assertThat(s.sky).isEqualTo("맑음")
        assertThat(s.temperature).isEqualTo(11)
        assertThat(s.suitability).isGreaterThanOrEqualTo(70)
    }

    @Test
    fun `하늘 상태 코드 → 텍스트`() {
        for ((code, text) in mapOf(1 to "맑음", 2 to "구름조금", 3 to "구름많음", 4 to "흐림", 7 to "정보없음", null to "정보없음")) {
            `when`(short.getAllShortTMEFDataForCell(anyInt(), anyInt()))
                .thenReturn(listOf(short(t(1), clear = true).copy(sky = code)))
            assertThat(service.getWeatherSummary(lat, lon).sky).`as`("sky=%s", code).isEqualTo(text)
        }
    }

    @Test
    fun `단기 예보가 없으면 초단기 예보로 대체한다`() {
        `when`(short.getAllShortTMEFDataForCell(anyInt(), anyInt())).thenReturn(emptyList())
        `when`(ultra.getAllUltraTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(
            ultra(t(2), clear = false, t1h = 3),
            ultra(t(1), clear = true, t1h = 5),
        ))

        val s = service.getWeatherSummary(lat, lon)

        assertThat(s.sky).isEqualTo("맑음")
        assertThat(s.temperature).isEqualTo(5)
        assertThat(s.suitability).isGreaterThanOrEqualTo(70)
    }

    @Test
    fun `예보가 전혀 없으면 적합도 0·정보없음·기온 null·다음 적합 시각 null`() {
        val s = service.getWeatherSummary(lat, lon)
        assertThat(s.suitability).isEqualTo(0)
        assertThat(s.sky).isEqualTo("정보없음")
        assertThat(s.temperature).isNull()
        assertThat(s.nextGoodTime).isNull()
    }

    @Test
    fun `다음 관측 적합 시각은 70점 이상인 첫 미래 시각`() {
        `when`(short.getAllShortTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(
            short(t(1), clear = false),
            short(t(2), clear = false),
            short(t(5), clear = true),
            short(t(3), clear = true), // 정렬돼 있지 않아도 가장 이른 시각
        ))

        assertThat(service.getWeatherSummary(lat, lon).nextGoodTime).isEqualTo(t(3))
    }

    @Test
    fun `초단기가 단기보다 이른 적합 시각을 주면 초단기를 택한다`() {
        `when`(short.getAllShortTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(short(t(4), clear = true)))
        `when`(ultra.getAllUltraTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(ultra(t(2), clear = true)))
        assertThat(service.getWeatherSummary(lat, lon).nextGoodTime).isEqualTo(t(2))

        `when`(ultra.getAllUltraTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(ultra(t(6), clear = true)))
        assertThat(service.getWeatherSummary(lat, lon).nextGoodTime).isEqualTo(t(4))
    }

    @Test
    fun `적합한 시각이 없으면 nextGoodTime 은 null`() {
        `when`(short.getAllShortTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(short(t(1), clear = false), short(t(2), clear = false)))
        `when`(ultra.getAllUltraTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(ultra(t(1), clear = false)))
        assertThat(service.getWeatherSummary(lat, lon).nextGoodTime).isNull()
    }

    @Test
    fun `과거 시각은 적합도가 높아도 다음 적합 시각이 되지 않는다`() {
        `when`(short.getAllShortTMEFDataForCell(anyInt(), anyInt())).thenReturn(listOf(short(t(-1), clear = true), short(t(1), clear = false)))
        assertThat(service.getWeatherSummary(lat, lon).nextGoodTime).isNull()
    }
}
