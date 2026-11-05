package com.project.byeoldori.forecast.utils.forecasts

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 기상청 발표시각(tmfc)·예보시각(tmef) 계산. 정각·30분 경계와 날짜 넘김을 고정 시각으로 확인하고,
 * 현재 시각에 기대는 단기예보 함수는 형식과 패턴 시각(2,5,...,23) 불변식만 본다.
 */
class ForecastTimeUtilTest {

    private val patternHours = setOf(2, 5, 8, 11, 14, 17, 20, 23)
    private val shortFmt = DateTimeFormatter.ofPattern("yyyyMMddHH")

    @Test
    fun `실황 tmfc 는 1시간 전 정각`() {
        assertThat(ForecastTimeUtil.getStableLiveTmfc(LocalDateTime.of(2026, 10, 20, 14, 37, 59)))
            .isEqualTo("202610201300")
        assertThat(ForecastTimeUtil.getStableLiveTmfc(LocalDateTime.of(2026, 10, 20, 14, 0, 0)))
            .isEqualTo("202610201300")
    }

    @Test
    fun `실황 tmfc 는 자정·월초를 넘어간다`() {
        assertThat(ForecastTimeUtil.getStableLiveTmfc(LocalDateTime.of(2026, 11, 1, 0, 10)))
            .isEqualTo("202610312300")
        assertThat(ForecastTimeUtil.getStableLiveTmfc(LocalDateTime.of(2027, 1, 1, 0, 0)))
            .isEqualTo("202612312300")
    }

    @Test
    fun `초단기 tmfc 는 60분 전을 30분 단위로 내림`() {
        assertThat(ForecastTimeUtil.getStableUltraTmfc(LocalDateTime.of(2026, 10, 20, 14, 29)))
            .isEqualTo("202610201300")
        assertThat(ForecastTimeUtil.getStableUltraTmfc(LocalDateTime.of(2026, 10, 20, 14, 30)))
            .isEqualTo("202610201330")
        assertThat(ForecastTimeUtil.getStableUltraTmfc(LocalDateTime.of(2026, 10, 20, 14, 59, 59)))
            .isEqualTo("202610201330")
        assertThat(ForecastTimeUtil.getStableUltraTmfc(LocalDateTime.of(2026, 10, 20, 0, 15)))
            .isEqualTo("202610192300")
    }

    @Test
    fun `초단기 tmef 는 다음 1~6시간 정각`() {
        val list = ForecastTimeUtil.getNext6UltraTmef(LocalDateTime.of(2026, 10, 20, 21, 45))
        assertThat(list).containsExactly(
            "202610202200", "202610202300", "202610210000",
            "202610210100", "202610210200", "202610210300",
        )
    }

    @Test
    fun `초단기 tmef 는 현재 시각 정각은 포함하지 않는다`() {
        val list = ForecastTimeUtil.getNext6UltraTmef(LocalDateTime.of(2026, 10, 20, 21, 0))
        assertThat(list).hasSize(6)
        assertThat(list.first()).isEqualTo("202610202200")
    }

    @Test
    fun `단기 tmfc 는 10자리이고 패턴 시각(2,5,8,11,14,17,20,23) 중 가장 최근 과거`() {
        val before = LocalDateTime.now()
        val tmfc = ForecastTimeUtil.getTMFCTimeForShort()
        val after = LocalDateTime.now()

        assertThat(tmfc).hasSize(10)
        val parsed = LocalDateTime.parse(tmfc, shortFmt)
        assertThat(parsed.hour).isIn(patternHours)
        assertThat(parsed).isBeforeOrEqualTo(after)
        // 발표시각은 3시간 주기이므로 현재보다 3시간 이상 이전일 수 없다
        assertThat(parsed).isAfter(before.minusHours(3).minusSeconds(1))
    }

    @Test
    fun `단기 tmef 목록은 tmfc 다음 시각부터 1시간 간격으로 자정에 끝난다`() {
        val tmfc = LocalDateTime.parse(ForecastTimeUtil.getTMFCTimeForShort(), shortFmt)
        val list = ForecastTimeUtil.getTMEFTimesForShortForecast()

        assertThat(list).isNotEmpty
        assertThat(list).allSatisfy { assertThat(it).hasSize(10) }
        val times = list.map { LocalDateTime.parse(it, shortFmt) }

        // 첫 시각은 발표시각 1시간 뒤(단, 두 호출 사이 발표가 바뀌는 경계는 ±3시간 허용)
        assertThat(times.first()).isAfterOrEqualTo(tmfc.plusHours(1))
        assertThat(times.first()).isBeforeOrEqualTo(tmfc.plusHours(4))

        // 1시간 간격, 오름차순
        times.zipWithNext().forEach { (a, b) -> assertThat(b).isEqualTo(a.plusHours(1)) }

        // 마지막은 자정(00시) — 02~14시 발표는 +3일, 17~23시 발표는 +4일 자정
        val last = times.last()
        assertThat(last.hour).isEqualTo(0)
        val expectedEnd = if (times.first().minusHours(1).hour in setOf(2, 5, 8, 11, 14))
            times.first().toLocalDate().plusDays(3).atStartOfDay()
        else times.first().toLocalDate().plusDays(4).atStartOfDay()
        // 첫 시각이 자정(=다음 날)인 경우 하루 어긋날 수 있어 두 값 중 하나
        assertThat(last).isIn(expectedEnd, expectedEnd.minusDays(1))
        assertThat(times.size).isBetween(48, 96)
    }
}
