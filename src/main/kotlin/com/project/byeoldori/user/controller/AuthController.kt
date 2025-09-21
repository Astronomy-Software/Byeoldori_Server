package com.project.byeoldori.user.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userService: UserService
) {

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    fun signup(@Valid @RequestBody req: SignupRequestDto): ResponseEntity<ApiResponse<Unit>> {
        userService.signup(req)
        return ResponseEntity.ok(ApiResponse.ok("가입 완료"))
    }

    @GetMapping("/verify-email")
    @Operation(summary = "이메일 인증")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<ApiResponse<Unit>> {
        userService.verifyEmail(token)
        return ResponseEntity.ok(ApiResponse.ok("이메일 인증 완료"))
    }

    @PostMapping("/login")
    @Operation(summary = "로그인")
    fun login(@Valid @RequestBody req: LoginRequestDto): ResponseEntity<ApiResponse<AuthResponseDto>> {
        val auth: AuthResponseDto = userService.login(req)
        return ResponseEntity.ok(ApiResponse.ok(auth))
    }

    @PostMapping("/token")
    @Operation(summary = "토큰 재발급")
    fun reissue(@Valid @RequestBody req: TokenReissueRequestDto): ResponseEntity<ApiResponse<AuthResponseDto>> {
        val auth: AuthResponseDto = userService.reissue(req)
        return ResponseEntity.ok(ApiResponse.ok(auth))
    }

    @PostMapping("/password/reset-request")
    @Operation(summary = "비밀번호 재설정 요청(메일 발송)")
    fun requestPassword(@Valid @RequestBody req: PasswordResetRequestDto): ResponseEntity<ApiResponse<Unit>> {
        userService.requestPasswordReset(req.email)
        return ResponseEntity.ok(ApiResponse.ok("재설정 메일 전송"))
    }

    @PostMapping("/password/reset-confirm")
    @Operation(summary = "비밀번호 재설정 확정")
    fun confirmPassword(@Valid @RequestBody req: PasswordResetConfirmDto): ResponseEntity<ApiResponse<Unit>> {
        userService.confirmPasswordReset(req.token, req.newPassword)
        return ResponseEntity.ok(ApiResponse.ok("비밀번호 변경 완료"))
    }
}