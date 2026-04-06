package com.project.byeoldori.notification.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.notification.dto.FcmTokenRequest
import com.project.byeoldori.notification.service.FcmTokenManageService
import com.project.byeoldori.security.CurrentUserResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "FCM", description = "푸시 알림 토큰 관리")
@RestController
@RequestMapping("/fcm")
class FcmTokenController(
    private val fcmTokenManageService: FcmTokenManageService,
    private val currentUserResolver: CurrentUserResolver
) {
    @Operation(summary = "FCM 토큰 등록", description = "디바이스 FCM 토큰을 서버에 등록합니다.")
    @PostMapping("/token")
    fun register(@Valid @RequestBody req: FcmTokenRequest): ResponseEntity<ApiResponse<Unit>> {
        fcmTokenManageService.register(currentUserResolver.getUser(), req.token, req.deviceType)
        return ResponseEntity.ok(ApiResponse.ok("FCM 토큰이 등록되었습니다."))
    }

    @Operation(summary = "FCM 토큰 삭제", description = "로그아웃 시 디바이스 토큰을 서버에서 제거합니다.")
    @DeleteMapping("/token")
    fun unregister(@Valid @RequestBody req: FcmTokenRequest): ResponseEntity<ApiResponse<Unit>> {
        fcmTokenManageService.unregister(currentUserResolver.getUser(), req.token)
        return ResponseEntity.ok(ApiResponse.ok("FCM 토큰이 삭제되었습니다."))
    }
}
