package com.project.byeoldori.forecast.utils.score

import com.project.byeoldori.forecast.dto.MidCombinedForecastDTO
import com.project.byeoldori.forecast.dto.ShortForecastResponseDTO
import com.project.byeoldori.forecast.dto.UltraForecastResponseDTO
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 관측 적합도 점수 계산. 각 요소 점수(0.0~1.0)는 응답 components 에 그대로 실리므로
 * 하늘·강수·바람·습도·달 각 분기와 가중 합성(total 0~100)을 따로 확인한다.
 */
class WeatherScoreCalculatorTest {

    private val astro = AstroCalculator()
    private val calc = WeatherScoreCalculator(astro)

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    private val seoulLat = 37.5665
    private val seoulLon = 126.9780

    private fun ultra(
        sky: Int? = 1, pty: Int? = 0, rn1: Float? = 0f, wsd: Float? = 1.5f, reh: Int? = 40,
        t1h: Int? = 10, vec: Int? = 180,
    ) = UltraForecastResponseDTO(tmef = "202610202100", t1h = t1h, vec = vec, wsd = wsd, pty = pty, rn1 = rn1, reh = reh, sky = sky)

    private fun short(
        tmef: String = "202610202100", sky: Int? = 1, pty: Int? = 0, pop: Int? = 0, pcp: Float? = null,
        wsd: Float? = 1.5f, reh: Int? = 40,
    ) = ShortForecastResponseDTO(
        tmef = tmef, tmp = 10, tmx = null, tmn = null, vec = null, wsd = wsd, sky = sky,
        pty = pty, pop = pop, pcp = pcp, sno = null, reh = reh,
    )

    private fun mid(tmEf: String = "202610231200", sky: String? = "WB01", pre: String? = "WB00", rnSt: Int? = 0) =
        MidCombinedForecastDTO(
            tmFc = "202610200600", tmEf = tmEf, doRegId = "11B00000", siRegId = "11B10101",
            sky = sky, pre = pre, rnSt = rnSt, min = 5, max = 15,
        )

    @Nested
    inner class 결측판정 {
        @Test
        fun `모든 값이 null 이면 결측`() {
            assertThat(calc.isUltraNoData(ultra(sky = null, pty = null, rn1 = null, wsd = null, reh = null, t1h = null, vec = null))).isTrue()
        }

        @Test
        fun `모든 값이 0 이고 sky 가 범위 밖이면 결측`() {
            assertThat(calc.isUltraNoData(ultra(sky = 0, pty = 0, rn1 = 0f, wsd = 0f, reh = 0, t1h = 0, vec = 0))).isTrue()
            assertThat(calc.isUltraNoData(ultra(sky = 5, pty = 0, rn1 = 0f, wsd = 0f, reh = 101, t1h = 0, vec = 0))).isTrue()
        }

        @Test
        fun `하나라도 유효한 값이 있으면 결측이 아니다`() {
            val base = ultra(sky = null, pty = 0, rn1 = 0f, wsd = 0f, reh = 0, t1h = 0, vec = 0)
            assertThat(calc.isUltraNoData(base)).isTrue()
            assertThat(calc.isUltraNoData(base.copy(sky = 1))).isFalse()
            assertThat(calc.isUltraNoData(base.copy(reh = 50))).isFalse()
            assertThat(calc.isUltraNoData(base.copy(reh = 100))).isFalse() // 100% 는 유효 범위 안
            assertThat(calc.isUltraNoData(base.copy(wsd = 0.1f))).isFalse()
            assertThat(calc.isUltraNoData(base.copy(t1h = -3))).isFalse()
            assertThat(calc.isUltraNoData(base.copy(vec = 90))).isFalse()
            assertThat(calc.isUltraNoData(base.copy(pty = 1))).isFalse()
            assertThat(calc.isUltraNoData(base.copy(rn1 = 0.5f))).isFalse()
        }
    }

    @Nested
    inner class 초단기 {
        @Test
        fun `결측이면 total 0 과 모든 요소 0 을 돌려준다`() {
            val s = calc.suitabilityForUltra(ultra(sky = null, pty = null, rn1 = null, wsd = null, reh = null, t1h = null, vec = null), 1.0)
            assertThat(s.total).isEqualTo(0)
            assertThat(s.components).isEqualTo(com.project.byeoldori.forecast.dto.SuitabilityScoreDTO.Components(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        }

        @Test
        fun `맑음·무강수·미풍·건조는 100점`() {
            val s = calc.suitabilityForUltra(ultra(sky = 1, pty = 0, rn1 = 0f, wsd = 1.5f, reh = 40), 0.0)
            assertThat(s.total).isEqualTo(100)
            assertThat(s.components.sky).isEqualTo(1.0)
            assertThat(s.components.precipitation).isEqualTo(1.0)
            assertThat(s.components.wind).isEqualTo(1.0)
            assertThat(s.components.humidity).isEqualTo(1.0)
            // 초단기는 달·광공해를 반영하지 않는다
            assertThat(s.components.moon).isEqualTo(0.0)
            assertThat(s.components.lightPollution).isEqualTo(0.0)
            assertThat(s.weights.moon).isEqualTo(0.0)
            assertThat(s.weights.lightPollution).isEqualTo(0.0)
        }

        @Test
        fun `광공해 점수는 초단기 total 에 영향을 주지 않는다`() {
            val a = calc.suitabilityForUltra(ultra(), 0.0)
            val b = calc.suitabilityForUltra(ultra(), 1.0)
            assertThat(a).isEqualTo(b)
        }

        @Test
        fun `하늘 상태 코드별 점수`() {
            assertThat(calc.suitabilityForUltra(ultra(sky = 1), 0.0).components.sky).isEqualTo(1.00)
            assertThat(calc.suitabilityForUltra(ultra(sky = 2), 0.0).components.sky).isEqualTo(0.75)
            assertThat(calc.suitabilityForUltra(ultra(sky = 3), 0.0).components.sky).isEqualTo(0.35)
            assertThat(calc.suitabilityForUltra(ultra(sky = 4), 0.0).components.sky).isEqualTo(0.05)
            // 범위 밖 코드는 중립
            assertThat(calc.suitabilityForUltra(ultra(sky = 9), 0.0).components.sky).isEqualTo(0.50)
            assertThat(calc.suitabilityForUltra(ultra(sky = null), 0.0).components.sky).isEqualTo(0.50)
        }

        @Test
        fun `강수 - 강수량이 있으면 0, 형태만 있으면 0_25, 없으면 1`() {
            assertThat(calc.suitabilityForUltra(ultra(pty = 0, rn1 = 0.1f), 0.0).components.precipitation).isEqualTo(0.0)
            assertThat(calc.suitabilityForUltra(ultra(pty = 3, rn1 = 0f), 0.0).components.precipitation).isEqualTo(0.25)
            assertThat(calc.suitabilityForUltra(ultra(pty = 3, rn1 = null), 0.0).components.precipitation).isEqualTo(0.25)
            assertThat(calc.suitabilityForUltra(ultra(pty = 0, rn1 = null), 0.0).components.precipitation).isEqualTo(1.0)
            assertThat(calc.suitabilityForUltra(ultra(pty = null, rn1 = null), 0.0).components.precipitation).isEqualTo(1.0)
        }

        @Test
        fun `풍속 구간 경계`() {
            fun wind(wsd: Float?) = calc.suitabilityForUltra(ultra(wsd = wsd), 0.0).components.wind
            assertThat(wind(null)).isEqualTo(0.5)
            assertThat(wind(0f)).isEqualTo(0.95)
            assertThat(wind(0.99f)).isEqualTo(0.95)
            assertThat(wind(1.0f)).isEqualTo(1.00)
            assertThat(wind(1.99f)).isEqualTo(1.00)
            assertThat(wind(2.0f)).isEqualTo(0.85)
            assertThat(wind(4.0f)).isEqualTo(0.80)
            assertThat(wind(6.0f)).isEqualTo(0.60)
            assertThat(wind(7.0f)).isEqualTo(0.50)
            assertThat(wind(9.0f)).isEqualTo(0.25)
            assertThat(wind(30f)).isEqualTo(0.25)
        }

        @Test
        fun `습도 구간 경계`() {
            fun hum(reh: Int?) = calc.suitabilityForUltra(ultra(reh = reh), 0.0).components.humidity
            assertThat(hum(null)).isEqualTo(0.5)
            assertThat(hum(10)).isEqualTo(1.00)
            assertThat(hum(54)).isEqualTo(1.00)
            assertThat(hum(55)).isEqualTo(0.90)
            assertThat(hum(65)).isEqualTo(0.70)
            assertThat(hum(75)).isEqualTo(0.45)
            assertThat(hum(85)).isEqualTo(0.30)
            assertThat(hum(92)).isEqualTo(0.18)
            assertThat(hum(100)).isEqualTo(0.18)
        }

        @Test
        fun `최악 조건은 한 자릿수 점수지만 0 은 아니다`() {
            // sky .05*.65 + precip 0*.20 + wind .25*.10 + hum .18*.05 = 0.0665 → 0.0665^0.97 ≈ 0.072 → 7
            val s = calc.suitabilityForUltra(ultra(sky = 4, pty = 1, rn1 = 2f, wsd = 12f, reh = 97), 0.0)
            assertThat(s.total).isEqualTo(7)
        }

        @Test
        fun `조건이 나빠질수록 total 은 단조 감소한다`() {
            val clear = calc.suitabilityForUltra(ultra(sky = 1), 0.0).total
            val fewClouds = calc.suitabilityForUltra(ultra(sky = 2), 0.0).total
            val cloudy = calc.suitabilityForUltra(ultra(sky = 3), 0.0).total
            val overcast = calc.suitabilityForUltra(ultra(sky = 4), 0.0).total
            assertThat(clear).isGreaterThan(fewClouds)
            assertThat(fewClouds).isGreaterThan(cloudy)
            assertThat(cloudy).isGreaterThan(overcast)
        }

        @Test
        fun `가중치 합은 1`() {
            val w = calc.suitabilityForUltra(ultra(), 0.0).weights
            assertThat(w.sky + w.precipitation + w.wind + w.humidity + w.moon + w.lightPollution).isCloseTo(1.0, within(1e-9))
        }
    }

    @Nested
    inner class 단기 {
        @Test
        fun `강수확률은 지수 스케일로 깎인다`() {
            fun precip(pop: Int?) = calc.suitabilityForShort(short(pop = pop), 0.0, seoulLat, seoulLon).components.precipitation
            assertThat(precip(null)).isEqualTo(1.0)
            assertThat(precip(0)).isEqualTo(1.0)
            assertThat(precip(100)).isEqualTo(0.0)
            assertThat(precip(50)).isCloseTo(1.0 - Math.pow(0.5, 1.15), within(1e-9))
            // 범위 밖 확률은 0~100 으로 보정
            assertThat(precip(-10)).isEqualTo(1.0)
            assertThat(precip(250)).isEqualTo(0.0)
            // 확률이 높을수록 점수는 낮다
            assertThat(precip(30)).isGreaterThan(precip(60))
            assertThat(precip(60)).isGreaterThan(precip(90))
        }

        @Test
        fun `강수량·강수형태가 있으면 확률과 무관하게 깎인다`() {
            assertThat(calc.suitabilityForShort(short(pop = 0, pcp = 0.5f), 0.0, seoulLat, seoulLon).components.precipitation).isEqualTo(0.0)
            assertThat(calc.suitabilityForShort(short(pop = 10, pty = 1), 0.0, seoulLat, seoulLon).components.precipitation).isEqualTo(0.25)
            // pcp=0 은 강수량 없음으로 본다
            assertThat(calc.suitabilityForShort(short(pop = 0, pcp = 0f), 0.0, seoulLat, seoulLon).components.precipitation).isEqualTo(1.0)
        }

        @Test
        fun `광공해 점수는 그대로 요소에 실린다`() {
            val s = calc.suitabilityForShort(short(), 0.3, seoulLat, seoulLon)
            assertThat(s.components.lightPollution).isEqualTo(0.3)
            assertThat(s.weights.lightPollution).isEqualTo(0.01)
        }

        @Test
        fun `tmef 는 10자리(시)와 12자리(분) 모두 받는다`() {
            val a = calc.suitabilityForShort(short(tmef = "2026102021"), 0.0, seoulLat, seoulLon)
            val b = calc.suitabilityForShort(short(tmef = "202610202100"), 0.0, seoulLat, seoulLon)
            assertThat(a).isEqualTo(b)
        }

        @Test
        fun `tmef 형식이 깨지면 예외`() {
            assertThatThrownBy { calc.suitabilityForShort(short(tmef = "20261020210"), 0.0, seoulLat, seoulLon) }
                .isInstanceOf(DateTimeParseException::class.java)
            assertThatThrownBy { calc.suitabilityForShort(short(tmef = "abcd"), 0.0, seoulLat, seoulLon) }
                .isInstanceOf(DateTimeParseException::class.java)
        }

        @Test
        fun `달이 지평선 아래면 달 점수 1, 떠 있으면 0_2 이상 1 이하`() {
            // 하루를 1시간 간격으로 훑어 달이 진 시각과 떠 있는 시각을 각각 찾는다
            val day = LocalDateTime.of(2026, 10, 20, 0, 0)
            val hours = (0 until 24).map { day.plusHours(it.toLong()) }
            val set = hours.first { astro.getMoonAltitude(it, seoulLat, seoulLon) < 0 }
            val up = hours.first { astro.getMoonAltitude(it, seoulLat, seoulLon) >= 0 }

            val setScore = calc.suitabilityForShort(short(tmef = set.format(fmt)), 0.0, seoulLat, seoulLon).components.moon
            val upScore = calc.suitabilityForShort(short(tmef = up.format(fmt)), 0.0, seoulLat, seoulLon).components.moon
            assertThat(setScore).isEqualTo(1.0)
            assertThat(upScore).isBetween(0.2, 1.0)

            val illum = astro.getMoonIlluminatedFraction(up)
            val altFactor = if (astro.getMoonAltitude(up, seoulLat, seoulLon) < 30) 0.7 else 1.0
            assertThat(upScore).isCloseTo((1.0 - 0.8 * illum * altFactor).coerceIn(0.2, 1.0), within(1e-9))
        }

        @Test
        fun `가중치 합은 1`() {
            val w = calc.suitabilityForShort(short(), 0.0, seoulLat, seoulLon).weights
            assertThat(w.sky + w.precipitation + w.wind + w.humidity + w.moon + w.lightPollution).isCloseTo(1.0, within(1e-9))
        }
    }

    @Nested
    inner class 중기 {
        @Test
        fun `WB 하늘 코드 매핑, 모르는 코드는 중립`() {
            fun sky(wb: String?) = calc.suitabilityForMid(mid(sky = wb), 0.0, seoulLat, seoulLon).components.sky
            assertThat(sky("WB01")).isEqualTo(1.00)
            assertThat(sky("WB02")).isEqualTo(0.75)
            assertThat(sky("WB03")).isEqualTo(0.35)
            assertThat(sky("WB04")).isEqualTo(0.05)
            assertThat(sky("WB99")).isEqualTo(0.50)
            assertThat(sky(null)).isEqualTo(0.50)
        }

        @Test
        fun `무강수 코드는 확률의 70%만 깎고, 강수 코드는 최대 0_3`() {
            fun precip(pre: String?, rnSt: Int?) = calc.suitabilityForMid(mid(pre = pre, rnSt = rnSt), 0.0, seoulLat, seoulLon).components.precipitation
            assertThat(precip(null, 0)).isEqualTo(1.0)
            assertThat(precip("WB00", 0)).isEqualTo(1.0)
            assertThat(precip("WB00", null)).isEqualTo(1.0)
            assertThat(precip("WB00", 100)).isCloseTo(0.3, within(1e-9))
            assertThat(precip("WB00", 50)).isCloseTo(0.65, within(1e-9))
            assertThat(precip("WB00", 150)).isCloseTo(0.3, within(1e-9)) // 100 으로 보정

            assertThat(precip("WB09", 0)).isCloseTo(0.3, within(1e-9))
            assertThat(precip("WB09", 100)).isEqualTo(0.0)
            assertThat(precip("WB09", null)).isEqualTo(0.0) // 확률 없음 → 100% 로 간주
            assertThat(precip("WB12", 50)).isCloseTo(0.15, within(1e-9))
        }

        @Test
        fun `바람·습도는 데이터가 없어 중립 0_5 이고 가중치 0`() {
            val s = calc.suitabilityForMid(mid(), 0.0, seoulLat, seoulLon)
            assertThat(s.components.wind).isEqualTo(0.5)
            assertThat(s.components.humidity).isEqualTo(0.5)
            assertThat(s.weights.wind).isEqualTo(0.0)
            assertThat(s.weights.humidity).isEqualTo(0.0)
        }

        @Test
        fun `tmEf 가 날짜만 오면 정오로 본다`() {
            val dateOnly = calc.suitabilityForMid(mid(tmEf = "20261023"), 0.0, seoulLat, seoulLon)
            val noon = calc.suitabilityForMid(mid(tmEf = "202610231200"), 0.0, seoulLat, seoulLon)
            assertThat(dateOnly).isEqualTo(noon)
        }

        @Test
        fun `맑음·무강수·달 없음·광공해 최상이면 100점`() {
            val day = LocalDateTime.of(2026, 10, 20, 0, 0)
            val set = (0 until 24).map { day.plusHours(it.toLong()) }.first { astro.getMoonAltitude(it, seoulLat, seoulLon) < 0 }
            val s = calc.suitabilityForMid(mid(tmEf = set.format(fmt), sky = "WB01", pre = "WB00", rnSt = 0), 1.0, seoulLat, seoulLon)
            assertThat(s.components.moon).isEqualTo(1.0)
            assertThat(s.total).isEqualTo(100)
        }

        @Test
        fun `가중치 합은 1`() {
            val w = calc.suitabilityForMid(mid(), 0.0, seoulLat, seoulLon).weights
            assertThat(w.sky + w.precipitation + w.wind + w.humidity + w.moon + w.lightPollution).isCloseTo(1.0, within(1e-9))
        }
    }
}
