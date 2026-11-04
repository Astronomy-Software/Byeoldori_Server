package com.project.byeoldori.forecast.utils.score

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.Cacheable

/**
 * 광공해 점수: 날것의 위경도를 Redis 캐시 키로 쓰면 좌표만 바꿔 불러 키가 끝없이 늘었다.
 * 캐시 없이 메모리 격자에서 계산하고, 같은 격자 칸의 좌표는 같은 값을 돌려준다.
 */
class LightPollutionTest {

    private val lp = LightPollution()

    @Test
    fun `요청 좌표를 Redis 캐시 키로 쓰지 않는다`() {
        val method = LightPollution::class.java.getMethod(
            "getLightPollutionScore", Double::class.javaPrimitiveType, Double::class.javaPrimitiveType
        )
        assertThat(method.getAnnotation(Cacheable::class.java)).isNull()
    }

    @Test
    fun `같은 격자 칸 안의 좌표는 소수점 자리가 달라도 같은 점수를 준다`() {
        // 서울 시청 부근과 바다 위(데이터 없는 칸일 수 있음) 모두 확인
        for ((lat, lon) in listOf(37.5665 to 126.9780, 34.0 to 124.5)) {
            val a = lp.getLightPollutionScore(lat, lon)
            val b = lp.getLightPollutionScore(lat + 0.0012345, lon - 0.0009876)
            assertThat(a).isEqualTo(b)
            assertThat(a).isBetween(0.0, 1.0)
        }
    }

    @Test
    fun `지원 범위 밖 좌표는 거부한다`() {
        assertThatThrownBy { lp.getLightPollutionScore(10.0, 126.0) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
