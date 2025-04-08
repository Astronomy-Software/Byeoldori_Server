package com.project.byeoldori.observation.controller

import com.project.byeoldori.observation.dto.ObservationSiteDto
import com.project.byeoldori.observation.entity.ObservationSite
import com.project.byeoldori.observation.service.ObservationSiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Observation Site", description = "관측지 API")
@RestController
@RequestMapping("/sites")
class ObservationSiteController(
    private val siteService: ObservationSiteService
) {
    @Operation(summary = "관측지 등록", description = "새로운 관측지 정보를 등록합니다.")
    @PostMapping
    fun create(@RequestBody @Valid dto: ObservationSiteDto): ResponseEntity<ObservationSite> {
        return ResponseEntity.ok(siteService.createObservationSite(dto))
    }

    @Operation(summary = "모든 관측지 조회", description = "등록된 모든 관측지 정보를 반환합니다.")
    @GetMapping
    fun getAll(): ResponseEntity<List<ObservationSite>> {
        return ResponseEntity.ok(siteService.getAllSites())
    }

    @Operation(summary = "관측지 검색", description = "키워드가 포함된 관측지 이름으로 검색합니다.")
    @GetMapping("/name")
    fun searchByName(@RequestParam keyword: String): ResponseEntity<List<ObservationSite>> {
        return ResponseEntity.ok(siteService.searchByName(keyword))
    }

    @Operation(summary = "관측지 수정", description = "해당 관측지 정보를 수정합니다.")
    @PutMapping("/{name}")
    fun updateByName(
        @PathVariable name: String,
        @RequestBody @Valid dto: ObservationSiteDto
    ): ResponseEntity<ObservationSite> {
        val updated = siteService.updateSiteByName(name, dto)
        return updated?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    @Operation(summary = "관측지 삭제", description = "해당 관측지 정보를 삭제합니다.")
    @DeleteMapping("/{name}")
    fun deleteByName(@PathVariable name: String): ResponseEntity<Void> {
        siteService.deleteSiteByName(name)
        return ResponseEntity.noContent().build()
    }
}
