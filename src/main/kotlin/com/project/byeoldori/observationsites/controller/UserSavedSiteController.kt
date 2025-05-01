package com.project.byeoldori.observationsites.controller

import com.project.byeoldori.observationsites.dto.ObservationSiteDto
import com.project.byeoldori.observationsites.service.UserSavedSiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

@Tag(name = "User Saved Sites", description = "사용자 저장 관측지 API")
@RestController
@RequestMapping("/users/{userId}/saved-sites")
class UserSavedSiteController(
    private val userSavedSiteService: UserSavedSiteService
) {

    @Operation(summary = "관측지 저장", description = "추천된 관측지를 사용자 즐겨찾기로 저장합니다.")
    @PostMapping
    fun saveSite(
        @PathVariable userId: String,
        @RequestParam siteId: Long
    ): ResponseEntity<String> {
        return try {
            val success = userSavedSiteService.saveSite(userId, siteId)
            if (success) ResponseEntity.ok("관측지가 저장되었습니다.")
            else ResponseEntity.ok("이미 저장된 관측지입니다.")
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @Operation(summary = "저장된 관측지 조회", description = "사용자가 저장한 관측지 ID 목록을 조회합니다.")
    @GetMapping
    fun getSavedSites(
        @PathVariable userId: String
    ): ResponseEntity<List<ObservationSiteDto>> {
        val savedSites = userSavedSiteService.getSavedSitesWithInfo(userId)
        return ResponseEntity.ok(savedSites)
    }

    @Operation(summary = "직접 입력한 관측지 저장", description = "사용자가 이름과 위치를 입력해 관측지를 직접 등록하고 저장합니다.")
    @PostMapping("/custom")
    fun saveCustomSite(
        @PathVariable userId: String,
        @RequestBody @Valid dto: ObservationSiteDto
    ): ResponseEntity<ObservationSiteDto> {
        val saved = userSavedSiteService.saveCustomSite(userId, dto)
        return ResponseEntity.ok(saved)
    }

    @Operation(summary = "관측지 즐겨찾기 삭제", description = "사용자가 저장한 관측지를 즐겨찾기 목록에서 삭제합니다.")
    @DeleteMapping("/{siteId}")
    fun deleteSavedSite(
        @PathVariable userId: String,
        @PathVariable siteId: Long
    ): ResponseEntity<Void> {
        return try {
            userSavedSiteService.deleteSavedSite(userId, siteId)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}
