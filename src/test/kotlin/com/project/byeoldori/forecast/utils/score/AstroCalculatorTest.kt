package com.project.byeoldori.forecast.utils.score

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * AstroCalculator 순수 천문 계산 검증 (Spring 컨텍스트 불필요).
 * 기준 신월: 2000-01-06 18:14 UTC, 평균 삭망월 29.53058867일.
 */
class AstroCalculatorTest {

    private val calc = AstroCalculator()
    private val utc = ZoneOffset.UTC

    @Test
    fun `조도 비율은 항상 0과 1 사이이다`() {
        val samples = listOf(
            LocalDateTime.of(2024, 1, 1, 0, 0),
            LocalDateTime.of(2024, 3, 15, 12, 0),
            LocalDateTime.of(2024, 7, 20, 6, 30),
            LocalDateTime.of(2025, 12, 31, 23, 59)
        )
        samples.forEach { dt ->
            val f = calc.getMoonIlluminatedFraction(dt, utc)
            assertThat(f).`as`("조도(%s)", dt).isBetween(0.0, 1.0)
        }
    }

    @Test
    fun `신월 시점의 조도는 0에 가깝다`() {
        val newMoon = LocalDateTime.of(2000, 1, 6, 18, 14)
        val f = calc.getMoonIlluminatedFraction(newMoon, utc)
        assertThat(f).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01))
    }

    @Test
    fun `반 삭망월 뒤 보름 시점의 조도는 1에 가깝다`() {
        // 신월 + (29.53058867/2)일 ≈ 21262분 → 보름
        val fullMoon = LocalDateTime.of(2000, 1, 6, 18, 14).plusMinutes(21262)
        val f = calc.getMoonIlluminatedFraction(fullMoon, utc)
        assertThat(f).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01))
    }

    @Test
    fun `달의 고도는 -90도에서 90도 범위 안에 있다`() {
        // 서울 좌표
        val lat = 37.5665
        val lon = 126.9780
        val samples = listOf(
            LocalDateTime.of(2024, 1, 1, 0, 0),
            LocalDateTime.of(2024, 6, 21, 12, 0),
            LocalDateTime.of(2025, 9, 10, 21, 0)
        )
        samples.forEach { dt ->
            val alt = calc.getMoonAltitude(dt, lat, lon, utc)
            assertThat(alt).`as`("고도(%s)", dt).isBetween(-90.0, 90.0)
        }
    }
}
