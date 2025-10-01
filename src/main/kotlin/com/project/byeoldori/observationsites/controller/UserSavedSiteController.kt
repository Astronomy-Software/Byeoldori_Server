package com.project.byeoldori.observationsites.controller

import com.project.byeoldori.observationsites.dto.ObservationSiteDto
import com.project.byeoldori.observationsites.service.UserSavedSiteService
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "User Saved Sites", description = "사용자 저장 관측지 API")
@RestController
@RequestMapping("/me/saved-sites")
class UserSavedSiteController(
    private val userSavedSiteService: UserSavedSiteService
) {

    @Operation(summary = "저장된 관측지 조회", description = "사용자가 저장한 관측지 목록을 조회합니다.")
    @GetMapping
    fun getSavedSites(@RequestAttribute("currentUser") user: User): ResponseEntity<List<ObservationSiteDto>> {
        val savedSites = userSavedSiteService.getSavedSites(user)
        return ResponseEntity.ok(savedSites)
    }

    @Operation(summary = "관측지 저장", description = "관측지를 사용자 즐겨찾기로 저장합니다.")
    @PostMapping("/{siteId}")
    fun saveSite(
        @RequestAttribute("currentUser") user: User,
        @PathVariable siteId: Long
    ): ResponseEntity<String> {
        userSavedSiteService.saveSite(user, siteId)
        return ResponseEntity.ok("관측지가 저장되었습니다.")
    }

    @Operation(summary = "관측지 즐겨찾기 삭제", description = "사용자가 저장한 관측지를 즐겨찾기 목록에서 삭제합니다.")
    @DeleteMapping("/{siteId}")
    fun deleteSavedSite(
        @RequestAttribute("currentUser") user: User,
        @PathVariable siteId: Long
    ): ResponseEntity<Map<String, String>> { // <- 수정: 반환 타입 변경

        // 1. 기존 삭제 로직 실행
        userSavedSiteService.deleteSite(user, siteId)

        // 2. 성공 메시지를 담은 응답 생성 및 반환
        val response = mapOf("message" to "관측지가 즐겨찾기에서 삭제되었습니다.")
        return ResponseEntity.ok(response)
    }
}