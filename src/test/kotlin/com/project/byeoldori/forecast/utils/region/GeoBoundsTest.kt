package com.project.byeoldori.forecast.utils.region

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GeoBoundsTest {

    @Test
    fun `경계값은 포함(닫힌 구간)`() {
        assertThat(GeoBounds.isInKorea(33.0, 124.0)).isTrue()
        assertThat(GeoBounds.isInKorea(38.7, 132.0)).isTrue()
        assertThat(GeoBounds.isInKorea(33.0, 132.0)).isTrue()
        assertThat(GeoBounds.isInKorea(38.7, 124.0)).isTrue()
    }

    @Test
    fun `경계 바로 밖은 제외`() {
        assertThat(GeoBounds.isInKorea(32.9999, 127.0)).isFalse()
        assertThat(GeoBounds.isInKorea(38.7001, 127.0)).isFalse()
        assertThat(GeoBounds.isInKorea(36.0, 123.9999)).isFalse()
        assertThat(GeoBounds.isInKorea(36.0, 132.0001)).isFalse()
    }

    @Test
    fun `주요 도시는 안, 인접국 도시는 밖`() {
        assertThat(GeoBounds.isInKorea(37.5665, 126.9780)).isTrue()  // 서울
        assertThat(GeoBounds.isInKorea(33.4996, 126.5312)).isTrue()  // 제주
        assertThat(GeoBounds.isInKorea(35.6762, 139.6503)).isFalse() // 도쿄
        assertThat(GeoBounds.isInKorea(39.9042, 116.4074)).isFalse() // 베이징
    }

    @Test
    fun `NaN 은 밖`() {
        assertThat(GeoBounds.isInKorea(Double.NaN, 127.0)).isFalse()
        assertThat(GeoBounds.isInKorea(36.0, Double.NaN)).isFalse()
    }
}
