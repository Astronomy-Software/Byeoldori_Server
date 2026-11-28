package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.entity.MidCombinedForecast
import com.project.byeoldori.forecast.repository.MidCombinedForecastRepository
import com.project.byeoldori.forecast.utils.forecasts.MidForecastParser
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil
import com.project.byeoldori.forecast.utils.forecasts.MidTempForecastParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono

/**
 * 중기 수집(fetchAndSaveFromApi): 기상청이 200 으로 빈 본문·주석만 있는 본문을 주면 저장 0건으로 "성공"하지 않고
 * 예외를 내 스케줄러가 재시도하게 한다(2026-11-27 06:11 사례). 정상 본문은 병합해 저장한다.
 */
class MidCombinedForecastFetchTest {

    private val repo = mock(MidCombinedForecastRepository::class.java)
    private val weatherData = mock(WeatherData::class.java)
    private val service = MidCombinedForecastService(MidForecastParser(), MidTempForecastParser(), repo, weatherData)

    // 도 코드 11B00000(서울·인천·경기) 한 줄 → RegionMapper 의 시 35개로 펼쳐진다
    private val landBody = """
        #START7777
        # REG_ID     TM_FC        TM_EF        MOD STN C  SKY  PRE  CONF WF      RN_ST
        11B00000  202611270600 202611300000 A02  109 1  WB01 WB00 WB40 맑음      10
        #7777END
    """.trimIndent()
    private val tempBody = """
        #START7777
        11B10101  202611270600 202611300000 A02  109 1   2  11  -1  1  -1  1
        #7777END
    """.trimIndent()

    private fun stub(land: String, temp: String) {
        `when`(weatherData.fetchMidLandForecast(anyString(), anyString())).thenReturn(Mono.just(land))
        `when`(weatherData.fetchMidTemperatureForecast(anyString(), anyString())).thenReturn(Mono.just(temp))
        `when`(repo.findByTmFcAndTmEfAndSiRegId(anyString(), anyString(), anyString())).thenReturn(null)
        `when`(repo.save(any(MidCombinedForecast::class.java))).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `빈 본문이면 예외를 내고 아무것도 저장하지 않는다`() {
        stub(land = "", temp = tempBody)

        assertThatThrownBy { service.fetchAndSaveFromApi() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("(빈 응답)")
        verify(repo, never()).save(any(MidCombinedForecast::class.java))
    }

    @Test
    fun `주석만 있는 본문(기상청 오류 문구)이면 예외 메시지에 본문 앞부분을 남기고 저장하지 않는다`() {
        stub(land = landBody, temp = "#START7777\n# dfs_file_read error (-1)\n#7777END")

        assertThatThrownBy { service.fetchAndSaveFromApi() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("dfs_file_read error")
        verify(repo, never()).save(any(MidCombinedForecast::class.java))
    }

    @Test
    fun `발표시각 범위는 지금 기준 지난 24시간을 yyyyMMddHH 로 넘긴다`() {
        // 2026-11-27 부터 기상청이 tmfc1·tmfc2 없이는 헤더만 돌려줘 저장 0건이 났다. 24시간 창이면 직전 06시·18시 발표가 항상 들어온다
        assertThat(ForecastTimeUtil.getMidForecastTmfcWindow(java.time.LocalDateTime.of(2026, 11, 27, 18, 10)))
            .isEqualTo("2026112618" to "2026112718")
        assertThat(ForecastTimeUtil.getMidForecastTmfcWindow(java.time.LocalDateTime.of(2026, 1, 1, 6, 10)))
            .isEqualTo("2025123106" to "2026010106")
    }

    @Test
    fun `기대하는 최근 발표시각은 06시·18시 기준으로 고른다`() {
        val d = { h: Int, m: Int -> java.time.LocalDateTime.of(2026, 11, 28, h, m) }
        assertThat(ForecastTimeUtil.getExpectedMidTmfc(d(6, 40))).isEqualTo("202611280600")
        assertThat(ForecastTimeUtil.getExpectedMidTmfc(d(17, 59))).isEqualTo("202611280600")
        assertThat(ForecastTimeUtil.getExpectedMidTmfc(d(18, 40))).isEqualTo("202611281800")
        assertThat(ForecastTimeUtil.getExpectedMidTmfc(d(23, 59))).isEqualTo("202611281800")
        assertThat(ForecastTimeUtil.getExpectedMidTmfc(d(5, 59))).isEqualTo("202611271800")  // 자정~06시 전은 어제 18시
    }

    @Test
    fun `호출할 때 발표시각 범위 두 값을 모두 넘긴다`() {
        stub(land = landBody, temp = tempBody)

        service.fetchAndSaveFromApi()

        val tmfc = org.mockito.ArgumentCaptor.forClass(String::class.java)
        // Kotlin 비null 파라미터라 capture() 의 null 을 그대로 넘기면 NPE → 빈 문자열로 대체(값은 captor 에 쌓인다)
        verify(weatherData).fetchMidLandForecast(tmfc.capture() ?: "", tmfc.capture() ?: "")
        assertThat(tmfc.allValues).hasSize(2).allMatch { it.matches(Regex("\\d{10}")) }
        assertThat(tmfc.allValues[0]).isLessThan(tmfc.allValues[1])
    }

    @Test
    fun `24시간 창에 두 발표분이 함께 오면 둘 다 (tmFc, tmEf, siRegId) 로 저장한다 - 조회는 tmEf 별 최신 tmFc 를 고른다`() {
        // 조회 쪽 최신 선택은 MidCombinedForecastSaveTest("06시·18시 발표가 함께 남아도 ...")가 검증한다
        val twoIssues = landBody.replace(
            "#7777END",
            "11B00000  202611271800 202611300000 A02  109 1  WB03 WB09 WB40 흐리고비  60\n#7777END"
        )
        stub(land = twoIssues, temp = tempBody)

        service.fetchAndSaveFromApi()

        val saved = org.mockito.ArgumentCaptor.forClass(MidCombinedForecast::class.java)
        verify(repo, times(70)).save(saved.capture())
        assertThat(saved.allValues.map { it.tmFc }.distinct()).containsExactlyInAnyOrder("202611270600", "202611271800")
        assertThat(saved.allValues.filter { it.tmFc == "202611271800" }).allMatch { it.sky == "WB03" && it.rnSt == 60 }
    }

    @Test
    fun `정상 본문은 시 단위로 병합해 저장한다`() {
        stub(land = landBody, temp = tempBody)

        val result = service.fetchAndSaveFromApi()

        assertThat(result).contains("35건")
        verify(repo, times(35)).save(any(MidCombinedForecast::class.java))
    }
}
