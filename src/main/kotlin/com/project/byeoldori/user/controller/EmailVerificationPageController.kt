package com.project.byeoldori.user.controller

import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

// 가입 메일의 인증 링크는 브라우저가 직접 연다 → JSON 이 아니라 HTML(thymeleaf) 페이지로 응답한다.
// @RestController 에서는 반환한 문자열이 뷰 이름이 아니라 본문("verification-success")으로 나가므로 따로 둔다.
@Controller
class EmailVerificationPageController(
    private val userService: UserService
) {

    @GetMapping("/auth/verify-email")
    @Operation(summary = "이메일 인증 (메일 링크용 HTML 페이지)")
    fun verifyEmail(@RequestParam token: String, model: Model): String {
        return try {
            userService.verifyEmail(token)
            "verification-success"
        } catch (e: InvalidInputException) {
            // 만료·이미 사용·잘못된 토큰: 안내 문구만 보여준다
            model.addAttribute("message", e.message)
            "verification-failed"
        }
    }
}
