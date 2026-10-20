package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.entity.MidCombinedForecast
import com.project.byeoldori.forecast.repository.MidCombinedForecastRepository
import com.project.byeoldori.forecast.utils.forecasts.MidForecastParser
import com.project.byeoldori.forecast.utils.forecasts.MidTempForecastParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

/**
 * 중기예보 저장(saveAll)을 실제 JPA(H2 MySQL 모드)로 검증한다.
 * 같은 (tmFc, tmEf, siRegId) 를 다시 받으면 유니크 키 충돌 없이 새 값으로 교체돼야 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MidCombinedForecastSaveTest @Autowired constructor(
    private val em: TestEntityManager,
    private val repo: MidCombinedForecastRepository,
) {
    private val service = MidCombinedForecastService(
        mock(MidForecastParser::class.java),
        mock(MidTempForecastParser::class.java),
        repo,
        mock(WeatherData::class.java),
    )

    private fun forecast(siRegId: String, sky: String, min: Int?) = MidCombinedForecast(
        tmFc = "202610200600", tmEf = "202610230000", doRegId = "11B00000", siRegId = siRegId,
        sky = sky, pre = "WB09", rnSt = 10, min = min, max = 15
    )

    @Test
    fun `같은 발표분을 다시 저장하면 기존 행을 새 값으로 교체한다`() {
        service.saveAll(listOf(forecast("11B10101", "WB01", 3), forecast("11B20201", "WB01", 4)))
        em.flush(); em.clear()

        service.saveAll(listOf(forecast("11B10101", "WB03", 5), forecast("11B20201", "WB04", 6)))
        em.flush(); em.clear()

        val rows = repo.findAll().sortedBy { it.siRegId }
        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.sky }).containsExactly("WB03", "WB04")
        assertThat(rows.map { it.min }).containsExactly(5, 6)
    }
}
