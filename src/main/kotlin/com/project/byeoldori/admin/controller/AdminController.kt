package com.project.byeoldori.admin.controller

import com.project.byeoldori.admin.dto.UpdateConfigRequest
import com.project.byeoldori.admin.service.SystemConfigService
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "관리자 전용 API")
class AdminController(
    private val systemConfigService: SystemConfigService
) {
    @GetMapping("/config")
    @Operation(summary = "시스템 설정 전체 조회", description = "관리자 전용. 전역 설정 키-값 전체를 반환합니다.")
    fun getConfig(
        @RequestAttribute("currentUser") user: User
    ): Map<String, String> {
        requireAdmin(user)
        return systemConfigService.getAll()
    }

    @PutMapping("/config")
    @Operation(summary = "시스템 설정 변경", description = "관리자 전용. 지정한 키의 값을 설정합니다.")
    fun updateConfig(
        @Valid @RequestBody req: UpdateConfigRequest,
        @RequestAttribute("currentUser") user: User
    ): ResponseEntity<ApiResponse<Unit>> {
        requireAdmin(user)
        systemConfigService.set(req.key, req.value)
        return ResponseEntity.ok(ApiResponse.ok("설정이 변경되었습니다."))
    }

    private fun requireAdmin(user: User) {
        if (!user.roles.contains("ADMIN")) throw ForbiddenException("관리자 권한이 필요합니다.")
    }
}
