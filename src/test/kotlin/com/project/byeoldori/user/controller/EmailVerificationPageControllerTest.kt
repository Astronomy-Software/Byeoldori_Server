package com.project.byeoldori.user.controller

import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

/**
 * 가입 메일의 인증 링크(GET /auth/verify-email)는 평문 문자열이 아니라 HTML 템플릿을 렌더링해야 한다.
 */
class EmailVerificationPageControllerTest {

    private val userService = mock(UserService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(EmailVerificationPageController(userService)).build()

    @Test
    fun `인증에 성공하면 완료 페이지 뷰를 렌더링한다`() {
        mvc.get("/auth/verify-email") { param("token", "ok") }
            .andExpect {
                status { isOk() }
                view { name("verification-success") }
            }
        verify(userService).verifyEmail("ok")
    }

    @Test
    fun `만료·사용된 토큰이면 JSON 대신 실패 안내 페이지를 렌더링한다`() {
        doThrow(InvalidInputException("토큰이 만료되었습니다.")).`when`(userService).verifyEmail("old")

        mvc.get("/auth/verify-email") { param("token", "old") }
            .andExpect {
                status { isOk() }
                view { name("verification-failed") }
                model { attribute("message", "토큰이 만료되었습니다.") }
            }
    }

    @Test
    fun `두 템플릿이 실제로 렌더링되고 실패 사유가 화면에 들어간다`() {
        val engine = SpringTemplateEngine().apply {
            setTemplateResolver(ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                characterEncoding = "UTF-8"
            })
        }
        assertThat(engine.process("verification-success", Context())).contains("이메일 인증 완료")

        val failed = engine.process("verification-failed", Context().apply { setVariable("message", "토큰이 만료되었습니다.") })
        assertThat(failed).contains("이메일 인증에 실패했습니다").contains("토큰이 만료되었습니다.")
    }
}
