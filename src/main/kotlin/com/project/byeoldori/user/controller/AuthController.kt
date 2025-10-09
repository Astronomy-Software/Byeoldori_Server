package com.project.byeoldori.user.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userService: UserService,
    private val session: HttpSession
) {

    @PostMapping("/signup")
    @Operation(summary = "회원가입")
    fun signup(@Valid @RequestBody req: SignupRequestDto): ResponseEntity<ApiResponse<Unit>> {
        userService.signup(req)
        return ResponseEntity.ok(ApiResponse.ok("가입 완료"))
    }

    @GetMapping("/verify-email")
    @Operation(summary = "이메일 인증")
    fun verifyEmail(@RequestParam token: String): String {
        userService.verifyEmail(token)
        return "verification-success"
    }

    @PostMapping("/find-email")
    @Operation(summary = "아이디(이메일) 찾기", description = "이름과 전화번호로 가입된 아이디 목록을 반환합니다.")
    fun findIds(@Valid @RequestBody req: FindEmailRequestDto)
            : ResponseEntity<ApiResponse<FindEmailResponseDto>> {
        val emails = userService.findEmailsByNameAndPhone(req.name, req.phone)
        return ResponseEntity.ok(ApiResponse.ok(FindEmailResponseDto(ids = emails)))
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
        userService.resetPasswordByIdentity(req)
        return ResponseEntity.ok(ApiResponse.ok("임시 비밀번호가 메일로 발송되었습니다."))
    }
    @Operation(summary = "구글 ID 토큰 로그인", description = "앱에서 받은 Google ID Token을 검증하고 Access/Refresh 토큰을 발급합니다.")
    @PostMapping("/google")
    @ResponseBody
    fun loginWithGoogle(@RequestBody req: GoogleLoginRequest): ResponseEntity<ApiResponse<AuthResponseDto>> {
        val tokens = userService.loginWithGoogleIdToken(req.idToken)
        return ResponseEntity.ok(ApiResponse.ok(tokens))
    }
}