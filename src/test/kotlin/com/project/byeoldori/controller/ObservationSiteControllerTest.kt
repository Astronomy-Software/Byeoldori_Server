package com.project.byeoldori.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.observationsites.controller.ObservationSiteController
import com.project.byeoldori.observationsites.dto.ObservationSiteDto
import com.project.byeoldori.observationsites.dto.RecommendationRequestDto
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.service.ObservationSiteRecommendationService
import com.project.byeoldori.observationsites.service.ObservationSiteService
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.databind.SerializationFeature
import com.project.byeoldori.observationsites.dto.ObservationSiteResponseDto

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

@ExtendWith(MockitoExtension::class)
class ObservationSiteControllerTest {

    @Mock
    private lateinit var siteService: ObservationSiteService
    @Mock
    private lateinit var siteRecommendationService: ObservationSiteRecommendationService
    private lateinit var controller: ObservationSiteController
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        controller = ObservationSiteController(siteService, siteRecommendationService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `관측지 등록`() {
        val dto = ObservationSiteDto("백두산", 41.985, 128.081)
        val response = ObservationSite(1, "백두산", 41.985, 128.081)

        `when`(siteService.createObservationSite(dto)).thenReturn(response)

        mockMvc.perform(
            post("/observationsites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name", `is`("백두산")))
    }

    @Test
    fun `모든 관측지 조회`() {
        val list = listOf(
            ObservationSite(1, "백두산", 41.0, 128.0),
            ObservationSite(2, "한라산", 33.0, 126.0)
        )

        `when`(siteService.getAllSites()).thenReturn(list)

        mockMvc.perform(get("/observationsites"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size()", `is`(2)))
            .andExpect(jsonPath("$[0].name", `is`("백두산")))
    }

    @Test
    fun `키워드로 관측지 검색`() {
        val list = listOf(ObservationSite(1, "백두산", 41.0, 128.0))

        `when`(siteService.searchByName("백")).thenReturn(list)

        mockMvc.perform(get("/observationsites/name").param("keyword", "백"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name", `is`("백두산")))
    }

    @Test
    fun `관측지 수정`() {
        val dto = ObservationSiteDto("한라산", 33.0, 126.0)
        val updated = ObservationSite(2, "한라산", 33.0, 126.0)

        `when`(siteService.updateSiteByName("지리산", dto)).thenReturn(updated)

        mockMvc.perform(
            put("/observationsites/지리산")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name", `is`("한라산")))
    }

    @Test
    fun `관측지 삭제`() {
        doNothing().`when`(siteService).deleteSiteByName("지리산")

        mockMvc.perform(delete("/observationsites/지리산"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `관측지 추천`() {
        val sampleRecommendation = listOf(
            ObservationSiteResponseDto("별마로 천문대", 37.197877, 128.486595, 0.85),
            ObservationSiteResponseDto("충북대학교", 37.5, 127.5, 0.65)
        )

        val request = RecommendationRequestDto(
            userLat = 37.5665,
            userLon = 126.9780,
            observationTime = LocalDateTime.of(2025, 4, 29, 21, 0)
        )

        `when`(
            siteRecommendationService.recommendSites(
                userLat = request.userLat,
                userLon = request.userLon,
                observationTime = request.observationTime
            )
        ).thenReturn(sampleRecommendation)

        mockMvc.perform(
            post("/observationsites/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size()", `is`(2)))
            .andExpect(jsonPath("$[0].name", `is`("별마로 천문대")))
            .andExpect(jsonPath("$[0].score", `is`(0.85)))
            .andExpect(jsonPath("$[1].name", `is`("충북대학교")))
            .andExpect(jsonPath("$[1].score", `is`(0.65)))
    }
}