package com.project.byeoldori.user.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.dto.*
import com.project.byeoldori.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    fun me(): ResponseEntity<ApiResponse<UserMeResponseDto>> {
        val me = userService.getMe()
        return ResponseEntity.ok(ApiResponse.ok(me))
    }

    @PatchMapping("/me")
    @Operation(summary = "내 정보 수정")
    fun update(@RequestBody req: UserUpdateRequestDto): ResponseEntity<ApiResponse<Unit>> {
        userService.updateMe(req)
        return ResponseEntity.ok(ApiResponse.ok("수정 완료"))
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃 (리프레시 토큰 제거)")
    fun logout(): ResponseEntity<ApiResponse<Unit>> {
        userService.logout()
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 완료"))
    }

    @PatchMapping("/password-change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "비밀번호 변경")
    fun changePassword(@Valid @RequestBody body: ChangePasswordRequest): ResponseEntity<ApiResponse<Unit>> {
        userService.changePassword(body)
        return ResponseEntity.ok(ApiResponse.ok("비밀번호 변경 완료"))
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴")
    fun deleteAccount(): ResponseEntity<ApiResponse<Unit>> {
        userService.deleteAccount()
        return ResponseEntity.ok(ApiResponse.ok("탈퇴 완료"))
    }
}