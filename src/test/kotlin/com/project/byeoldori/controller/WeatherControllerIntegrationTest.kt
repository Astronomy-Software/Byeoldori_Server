package com.project.byeoldori.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = ["/data.sql"])
class WeatherControllerIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `ForecastData endpoint returns valid response`() {
        // 예시: 위경도 (37.56357, 126.98)가 RegionMapper를 통해 "서울특별시"에 매핑된다고 가정
        val lat = 37.56357
        val lon = 126.98

        val result = mockMvc.get("/weather/ForecastData?lat=$lat&long=$lon")
            .andExpect {
                status { isOk() }
            }
            .andReturn()

        val content = result.response.contentAsString
        println("ForecastData Response: $content")

        // 응답 내용이 비어있지 않은지 확인
        assertTrue(content.isNotEmpty(), "응답 내용은 비어있으면 안됩니다.")
    }
}
