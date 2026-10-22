package com.project.byeoldori.education.program.controller

import com.project.byeoldori.admin.service.SystemConfigService
import com.project.byeoldori.common.web.ApiResponseWrapperAdvice
import com.project.byeoldori.education.program.domain.ProgramStatus
import com.project.byeoldori.education.program.dto.ProgramDetailResponse
import com.project.byeoldori.education.program.service.EducationProgramService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * GET /education/programs/moderation 응답 봉투와, 리터럴 경로가 /{id} 와 충돌하지 않는지 (Spring 컨텍스트 불필요).
 */
class EducationProgramControllerTest {

    private val service = mock(EducationProgramService::class.java)
    private val systemConfig = mock(SystemConfigService::class.java)
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(EducationProgramController(service, systemConfig))
        .setControllerAdvice(ApiResponseWrapperAdvice())
        .build()

    @Test
    fun `검수 필요 여부를 ApiResponse 봉투로 돌려준다`() {
        for (required in listOf(true, false)) {
            `when`(systemConfig.moderationRequired()).thenReturn(required)
            mockMvc.perform(get("/education/programs/moderation"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.required").value(required))
        }
        // moderation 이 프로그램 id 로 해석되면 안 된다
        verify(service, never()).get("moderation", null)
    }

    @Test
    fun `프로그램 상세 경로는 그대로 동작한다`() {
        `when`(service.get("prog-1", null)).thenReturn(
            ProgramDetailResponse(
                id = "prog-1", title = "여름철 별자리", subtitle = null, difficulty = null, schemaVersion = 1,
                steps = emptyList(), status = ProgramStatus.PUBLISHED, authorId = 1, authorName = "작성자", rejectReason = null
            )
        )
        mockMvc.perform(get("/education/programs/prog-1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value("prog-1"))
            .andExpect(jsonPath("$.data.title").value("여름철 별자리"))
    }
}
