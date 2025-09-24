package com.project.byeoldori.user.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.service.UserService
import com.project.byeoldori.user.utils.PWD_RESET_V_UID
import com.project.byeoldori.user.utils.PasswordValidator
import io.swagger.v3.oas.annotations.Operation
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    fun me(auth: Authentication): ResponseEntity<ApiResponse<UserMeResponseDto>> {
        val me = userService.getMe(auth.name)
        return ResponseEntity.ok(ApiResponse.ok(me))
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정")
    fun update(auth: Authentication, @RequestBody req: UserUpdateRequestDto): ResponseEntity<ApiResponse<Unit>> {
        userService.updateMe(auth.name, req)
        return ResponseEntity.ok(ApiResponse.ok("수정 완료"))
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃 (리프레시 토큰 제거)")
    fun logout(auth: Authentication): ResponseEntity<ApiResponse<Unit>> {
        userService.logout(auth.name)
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 완료"))
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴")
    fun deleteAccount(auth: Authentication): ResponseEntity<ApiResponse<Unit>> {
        userService.deleteAccount(auth.name)
        return ResponseEntity.ok(ApiResponse.ok("탈퇴 완료"))
    }

    @PostMapping("/password-reset")
    @Operation(
        summary = "비밀번호 재설정")
    fun resetPasswordConfirm(
        @Valid @RequestBody req: PasswordResetDto,
        session: HttpSession
    ): ResponseEntity<ApiResponse<Unit>> {
        require(req.password == req.confirmPassword) { "비밀번호와 재입력 값이 일치하지 않습니다." }
        require(PasswordValidator.isValid(req.password)) { "비밀번호 정책을 확인하세요." }

        val userId = session.getAttribute(PWD_RESET_V_UID) as? Long
            ?: throw IllegalArgumentException("비밀번호 재설정 인증이 유효하지 않습니다.")

        userService.resetPasswordByUserId(userId, req.password)

        // 재사용 방지
        session.removeAttribute(PWD_RESET_V_UID)

        return ResponseEntity.ok(ApiResponse.ok("비밀번호가 재설정되었습니다."))
    }
}