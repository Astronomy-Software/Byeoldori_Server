package com.project.byeoldori.service

import com.project.byeoldori.observationsites.service.LightPollutionService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LightPollutionServiceTest {

    private val service = LightPollutionService()

    @Test
    fun `서울 광공해 점수는 낮아야 한다`() {
        val lat = 37.5665
        val lon = 126.9780
        val score = service.getLightPollutionScore(lat, lon)
        println("서울 광공해 점수: $score")
        assertTrue(score < 0.5, "서울은 도심이므로 점수가 낮아야 함")
    }

    @Test
    fun `강원도 산간 지역 점수는 높아야 한다`() {
        val lat = 38.1
        val lon = 128.2
        val score = service.getLightPollutionScore(lat, lon)
        println("강원도 광공해 점수: $score")
        assertTrue(score > 0.7, "산간 지역이므로 점수가 높아야 함")
    }

    @Test
    fun `대한민국 외 지역은 예외를 던져야 한다`() {
        val lat = 11.0
        val lon = 111.0
        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.getLightPollutionScore(lat, lon)
        }
        println("예외 메시지: ${exception.message}")
        assertTrue(exception.message!!.contains("지원되지 않는 지역"))
    }

}
