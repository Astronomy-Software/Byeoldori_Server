package com.project.byeoldori.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.observation.controller.ObservationSiteController
import com.project.byeoldori.observation.dto.ObservationSiteDto
import com.project.byeoldori.observation.entity.ObservationSite
import com.project.byeoldori.observation.service.ObservationSiteService
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

@ExtendWith(MockitoExtension::class)
class ObservationSiteControllerTest {

    @Mock
    private lateinit var siteService: ObservationSiteService
    private lateinit var controller: ObservationSiteController
    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        controller = ObservationSiteController(siteService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }
    @Test
    fun `관측지 등록`() {
        val dto = ObservationSiteDto("백두산", 41.985, 128.081)
        val response = ObservationSite(1L, "백두산", 41.985, 128.081)

        `when`(siteService.createObservationSite(dto)).thenReturn(response)

        mockMvc.perform(
            post("/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name", `is`("백두산")))
    }

    @Test
    fun `모든 관측지 조회`() {
        val list = listOf(
            ObservationSite(1L, "백두산", 41.0, 128.0),
            ObservationSite(2L, "한라산", 33.0, 126.0)
        )

        `when`(siteService.getAllSites()).thenReturn(list)

        mockMvc.perform(get("/sites"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size()", `is`(2)))
            .andExpect(jsonPath("$[0].name", `is`("백두산")))
    }

    @Test
    fun `관측지 이름으로 검색`() {
        val list = listOf(ObservationSite(1L, "백두산", 41.0, 128.0))

        `when`(siteService.searchByName("백")).thenReturn(list)

        mockMvc.perform(get("/sites/name").param("keyword", "백"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name", `is`("백두산")))
    }

    @Test
    fun `관측지 이름 기준 수정`() {
        val dto = ObservationSiteDto("한라산", 33.0, 126.0)
        val updated = ObservationSite(2L, "한라산", 33.0, 126.0)

        `when`(siteService.updateSiteByName("지리산", dto)).thenReturn(updated)

        mockMvc.perform(
            put("/sites/name/지리산")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name", `is`("한라산")))
    }

    @Test
    fun `관측지 삭제`() {
        doNothing().`when`(siteService).deleteSiteByName("지리산")

        mockMvc.perform(delete("/sites/name/지리산"))
            .andExpect(status().isNoContent)
    }
}
