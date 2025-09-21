package com.project.byeoldori.user.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.dto.UserUpdateRequestDto
import com.project.byeoldori.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
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
    fun me(auth: Authentication): ResponseEntity<ApiResponse<Any>> {
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
}