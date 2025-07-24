package com.project.byeoldori.user.controller

import com.project.byeoldori.user.utils.ApiResponse
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.repository.EmailVerificationTokenRepository
import com.project.byeoldori.user.repository.UserRepository
import com.project.byeoldori.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "회원가입, 로그인, 인증, 계정 관리 관련 API")
class UserController(
    private val userService: UserService,
    private val tokenRepository: EmailVerificationTokenRepository,
    private val userRepository: UserRepository
) {

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일을 통한 신규 회원가입을 처리합니다.")
    fun signup(@Valid @RequestBody request: SignupRequestDto): ResponseEntity<ApiResponse<Map<String, String>>> {
        userService.signup(request)
        val response = mapOf("redirectTo" to "/login")
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse(true, "회원가입이 완료되었습니다.", response)
        )
    }

    @GetMapping("/verify-email")
    @Operation(summary = "이메일 인증 처리", description = "회원가입 후 이메일로 받은 토큰을 통해 인증을 완료합니다.")
    fun verifyEmail(@RequestParam token: String): ResponseEntity<ApiResponse<String?>> {
        val verificationToken = tokenRepository.findByTokenWithUser(token)
            ?: throw IllegalArgumentException("유효하지 않은 인증 토큰입니다.")

        if (verificationToken.verified) {
            return ResponseEntity.badRequest().body(ApiResponse(false, "이미 인증이 완료된 이메일입니다.", null))
        }

        if (verificationToken.expiresAt.isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(ApiResponse(false, "인증 링크가 만료되었습니다.", null))
        }

        val user = verificationToken.user
        user.emailVerified = true
        verificationToken.verified = true

        userRepository.save(user)
        tokenRepository.save(verificationToken)

        return ResponseEntity.ok(ApiResponse(true, "이메일 인증이 완료되었습니다. 이제 로그인하실 수 있습니다.", null))
    }

    @GetMapping("/check-email")
    @Operation(summary = "이메일 중복 확인", description = "입력한 이메일이 이미 사용 중인지 확인합니다.")
    fun checkEmail(@RequestParam email: String): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
        val exists = userRepository.existsByEmail(email)
        return ResponseEntity.ok(ApiResponse(true, "이메일 중복 확인 완료", mapOf("exists" to exists)))
    }

    @GetMapping("/check-nickname")
    @Operation(summary = "닉네임 중복 확인", description = "입력한 닉네임이 이미 사용 중인지 확인합니다.")
    fun checkNickname(@RequestParam nickname: String): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
        val exists = userRepository.existsByNickname(nickname)
        return ResponseEntity.ok(ApiResponse(true, "닉네임 중복 확인 완료", mapOf("exists" to exists)))
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호를 통해 로그인하고 JWT 토큰을 발급받습니다.")
    fun login(@Valid @RequestBody request: LoginRequestDto): ResponseEntity<ApiResponse<Map<String, String>>> {
        val tokens = userService.login(request)
        return ResponseEntity.ok(ApiResponse(true, "로그인 성공", tokens))
    }

    @PostMapping("/reissue")
    @Operation(summary = "Access Token 재발급", description = "Refresh Token으로 새로운 Access Token을 발급합니다.")
    fun reissue(@RequestBody request: TokenReissueRequestDto): ResponseEntity<ApiResponse<Map<String, String>>> {
        val newAccessToken = userService.reissue(request)
        return ResponseEntity.ok(ApiResponse(true, "Access Token 재발급 완료", mapOf("accessToken" to newAccessToken)))
    }

    @PatchMapping("/me")
    @Operation(summary = "사용자 정보 수정", description = "닉네임과 생일 정보를 업데이트합니다.")
    fun updateUserInfo(
        @RequestBody request: UserUpdateRequestDto,
        authentication: Authentication
    ): ResponseEntity<ApiResponse<String?>> {
        val email = authentication.name
        userService.updateUserInfo(email, request)
        return ResponseEntity.ok(ApiResponse(true, "사용자 정보가 업데이트되었습니다.", null))
    }

    @PostMapping("/find-email")
    @Operation(summary = "이메일 찾기", description = "이름과 전화번호를 입력하여 등록된 이메일을 조회합니다.")
    fun findEmail(@RequestBody request: FindEmailRequestDto): ResponseEntity<ApiResponse<String>> {
        val email = userService.findEmailByNameAndPhone(request.name, request.phone)
        return ResponseEntity.ok(ApiResponse(true, "이메일 조회 성공", email))
    }

    @PostMapping("/reset-password-request")
    @Operation(summary = "비밀번호 재설정 요청", description = "입력한 이메일로 비밀번호 재설정 링크를 전송합니다.")
    fun requestPasswordReset(@RequestBody request: PasswordResetRequestDto): ResponseEntity<ApiResponse<String?>> {
        userService.sendPasswordResetEmail(request.email)
        return ResponseEntity.ok(ApiResponse(true, "비밀번호 재설정 이메일이 전송되었습니다.", null))
    }

    @PostMapping("/reset-password")
    @Operation(summary = "비밀번호 재설정", description = "토큰을 검증하고 새 비밀번호로 변경합니다.")
    fun resetPassword(@RequestBody request: PasswordResetConfirmDto): ResponseEntity<ApiResponse<String?>> {
        userService.resetPassword(request.token, request.newPassword)
        return ResponseEntity.ok(ApiResponse(true, "비밀번호가 성공적으로 변경되었습니다.", null))
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "Refresh Token을 삭제하여 로그아웃 처리합니다.")
    fun logout(authentication: Authentication): ResponseEntity<ApiResponse<String?>> {
        val email = authentication.name
        userService.logout(email)
        return ResponseEntity.ok(ApiResponse(true, "로그아웃 되었습니다.", null))
    }

    @DeleteMapping("/delete-email")
    @Operation(summary = "회원 탈퇴", description = "현재 로그인된 사용자의 계정을 삭제합니다.")
    fun deleteAccount(authentication: Authentication): ResponseEntity<ApiResponse<String?>> {
        val email = authentication.name
        userService.deleteAccount(email)
        return ResponseEntity.ok(ApiResponse(true, "회원 탈퇴가 완료되었습니다.", null))
    }
}