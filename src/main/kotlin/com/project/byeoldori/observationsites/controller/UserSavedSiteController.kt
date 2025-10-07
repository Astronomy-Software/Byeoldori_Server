package com.project.byeoldori.observationsites.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.observationsites.dto.*
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
    fun getSavedSites(@RequestAttribute("currentUser") user: User): ResponseEntity<List<SavedSiteResponseDto>> {
        val savedSites = userSavedSiteService.getSavedSites(user)
        return ResponseEntity.ok(savedSites)
    }

    @Operation(summary = "즐겨찾기 토글", description = "즐겨찾기 상태를 추가 또는 삭제로 전환합니다." +
            "저장된 관측지 즐겨찾기 할 경우 siteId만 입력, 임의 장소 즐겨찾기 할 경우 name, lat, lon 3개만 입력")
    @PostMapping("/toggle")
    fun toggleSavedSite(
        @RequestAttribute("currentUser") user: User,
        @RequestBody request: SiteToggleRequest
    ): ResponseEntity<ApiResponse<SiteToggleResponse>> {
        val response = userSavedSiteService.toggleSite(user, request)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @Operation(summary = "저장된 관측지 상세 조회", description = "즐겨찾기한 장소의 정보를 상세 조회합니다.")
    @GetMapping("/{savedSiteId}")
    fun getSavedSiteDetail(
        @RequestAttribute("currentUser") user: User,
        @PathVariable savedSiteId: Long
    ): ResponseEntity<SavedSiteResponseDto> {
        val savedSite = userSavedSiteService.getSavedSiteDetail(user, savedSiteId)
        return ResponseEntity.ok(savedSite)
    }

    @Operation(summary = "관측지 즐겨찾기 삭제", description = "사용자가 저장한 관측지를 즐겨찾기 목록에서 삭제합니다.")
    @DeleteMapping("/{savedSiteId}")
    fun deleteSavedSite(
        @RequestAttribute("currentUser") user: User,
        @PathVariable savedSiteId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        userSavedSiteService.deleteSavedSite(user, savedSiteId)
        return ResponseEntity.ok(ApiResponse.ok("즐겨찾기에서 삭제되었습니다."))
    }
}