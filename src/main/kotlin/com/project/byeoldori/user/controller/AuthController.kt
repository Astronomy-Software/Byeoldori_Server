package com.project.byeoldori.user.controller

import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.UnauthorizedException
import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userService: UserService,
    private val jwt: JwtUtil
) {

    private companion object {
        const val REFRESH_COOKIE = "refreshToken"
    }

    // refresh 토큰을 httpOnly 쿠키로 내려준다(웹 XSS 완화). body 토큰은 그대로 유지(안드로이드 호환).
    // Domain 속성은 절대 넣지 않는다(프론트가 다른 도메인 + 프록시 경유라 host-only 여야 함).
    private fun addRefreshCookie(response: HttpServletResponse, refreshToken: String) {
        val cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofSeconds(jwt.refreshTokenTtlSeconds))
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
    }

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
    fun login(
        @Valid @RequestBody req: LoginRequestDto,
        response: HttpServletResponse
    ): ResponseEntity<ApiResponse<AuthResponseDto>> {
        val auth: AuthResponseDto = userService.login(req)
        addRefreshCookie(response, auth.refreshToken)
        return ResponseEntity.ok(ApiResponse.ok(auth))
    }

    @PostMapping("/token")
    @Operation(summary = "토큰 재발급")
    fun reissue(
        @CookieValue(name = REFRESH_COOKIE, required = false) cookieRefresh: String?,
        @RequestBody(required = false) body: TokenReissueRequestDto?,
        response: HttpServletResponse
    ): ResponseEntity<ApiResponse<AuthResponseDto>> {
        // 웹: httpOnly 쿠키 우선. 안드로이드: 쿠키가 없으면 기존 body 사용.
        val refreshToken = cookieRefresh?.takeIf { it.isNotBlank() }
            ?: body?.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw UnauthorizedException(ErrorCode.INVALID_TOKEN.message)

        val auth: AuthResponseDto = userService.reissue(refreshToken)
        addRefreshCookie(response, auth.refreshToken)
        return ResponseEntity.ok(ApiResponse.ok(auth))
    }

    @PostMapping("/password/reset-request")
    @Operation(summary = "비밀번호 재설정 요청(메일 발송)", description = "이름/이메일/전화번호 확인 후 재설정 링크를 메일로 발송합니다.")
    fun requestPassword(@Valid @RequestBody req: PasswordResetRequestDto): ResponseEntity<ApiResponse<Unit>> {
        userService.resetPasswordByIdentity(req)
        return ResponseEntity.ok(ApiResponse.ok("비밀번호 재설정 링크가 메일로 발송되었습니다."))
    }

    @PostMapping("/password/reset-confirm")
    @Operation(summary = "비밀번호 재설정 확인", description = "메일로 받은 토큰과 새 비밀번호를 입력하여 비밀번호를 변경합니다.")
    fun confirmPasswordReset(@Valid @RequestBody req: PasswordResetConfirmDto): ResponseEntity<ApiResponse<Unit>> {
        userService.confirmPasswordReset(req)
        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 재설정되었습니다."))
    }

    @PostMapping("/google")
    @Operation(summary = "Google 소셜 로그인", description = "프론트에서 받은 Authorization Code로 Google 로그인 처리 후 JWT를 발급합니다.")
    fun loginWithGoogle(
        @RequestBody req: GoogleLoginRequest,
        response: HttpServletResponse
    ): ResponseEntity<ApiResponse<AuthResponseDto>> {
        val tokens = userService.loginWithGoogle(req.code, req.redirectUri)
        addRefreshCookie(response, tokens.refreshToken)
        return ResponseEntity.ok(ApiResponse.ok(tokens))
    }

    @PostMapping("/kakao")
    @Operation(summary = "Kakao 소셜 로그인", description = "프론트에서 받은 Authorization Code로 Kakao 로그인 처리 후 JWT를 발급합니다.")
    fun loginWithKakao(
        @RequestBody req: KakaoLoginRequest,
        response: HttpServletResponse
    ): ResponseEntity<ApiResponse<AuthResponseDto>> {
        val tokens = userService.loginWithKakao(req.code, req.redirectUri)
        addRefreshCookie(response, tokens.refreshToken)
        return ResponseEntity.ok(ApiResponse.ok(tokens))
    }

    @PostMapping("/naver")
    @Operation(summary = "Naver 소셜 로그인", description = "프론트에서 받은 Authorization Code로 Naver 로그인 처리 후 JWT를 발급합니다.")
    fun loginWithNaver(
        @RequestBody req: NaverLoginRequest,
        response: HttpServletResponse
    ): ResponseEntity<ApiResponse<AuthResponseDto>> {
        val tokens = userService.loginWithNaver(req.code, req.redirectUri)
        addRefreshCookie(response, tokens.refreshToken)
        return ResponseEntity.ok(ApiResponse.ok(tokens))
    }
}
