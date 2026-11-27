package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.entity.MidCombinedForecast
import com.project.byeoldori.forecast.repository.MidCombinedForecastRepository
import com.project.byeoldori.forecast.utils.forecasts.MidForecastParser
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
        `when`(weatherData.fetchMidLandForecast()).thenReturn(Mono.just(land))
        `when`(weatherData.fetchMidTemperatureForecast()).thenReturn(Mono.just(temp))
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
    fun `정상 본문은 시 단위로 병합해 저장한다`() {
        stub(land = landBody, temp = tempBody)

        val result = service.fetchAndSaveFromApi()

        assertThat(result).contains("35건")
        verify(repo, times(35)).save(any(MidCombinedForecast::class.java))
    }
}
