package com.project.byeoldori.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.project.byeoldori.observationsites.controller.ObservationSiteController
import com.project.byeoldori.observationsites.dto.ObservationSiteDto
import com.project.byeoldori.observationsites.dto.ObservationSiteResponseDto
import com.project.byeoldori.observationsites.service.ObservationSiteService
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
}

@ExtendWith(MockitoExtension::class)
class ObservationSiteControllerTest {

    @Mock
    private lateinit var siteService: ObservationSiteService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        val controller = ObservationSiteController(siteService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `관측지 등록`() {
        val dto = ObservationSiteDto("백두산", 41.985, 128.081)
        val response = ObservationSiteResponseDto(1L, "백두산", 41.985, 128.081)

        `when`(siteService.createObservationSite(dto)).thenReturn(response)

        mockMvc.perform(
            post("/observationsites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name", `is`("백두산")))
    }

    @Test
    fun `모든 관측지 조회`() {
        val list = listOf(
            ObservationSiteResponseDto(1L, "백두산", 41.0, 128.0),
            ObservationSiteResponseDto(2L, "한라산", 33.0, 126.0)
        )
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(list, pageable, list.size.toLong())

        `when`(siteService.getAllSites(pageable)).thenReturn(page)

        mockMvc.perform(get("/observationsites"))
            .andExpect(status().isOk)
    }

    @Test
    fun `키워드로 관측지 검색`() {
        val list = listOf(ObservationSiteResponseDto(1L, "백두산", 41.0, 128.0))

        `when`(siteService.searchByName("백")).thenReturn(list)

        mockMvc.perform(get("/observationsites/name").param("keyword", "백"))
            .andExpect(status().isOk)
    }

    @Test
    fun `관측지 수정`() {
        val dto = ObservationSiteDto("한라산", 33.0, 126.0)
        val updated = ObservationSiteResponseDto(2L, "한라산", 33.0, 126.0)

        `when`(siteService.updateSiteById(2L, dto)).thenReturn(updated)

        mockMvc.perform(
            put("/observationsites/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `관측지 삭제`() {
        doNothing().`when`(siteService).deleteSiteById(2L)

        mockMvc.perform(delete("/observationsites/2"))
            .andExpect(status().isNoContent)
    }
}
