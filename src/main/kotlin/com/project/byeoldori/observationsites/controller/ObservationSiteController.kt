package com.project.byeoldori.observationsites.controller

import com.project.byeoldori.observationsites.dto.ObservationSiteDto
import com.project.byeoldori.observationsites.dto.ObservationSiteResponseDto
import com.project.byeoldori.observationsites.dto.RecommendationRequestDto
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.service.ObservationSiteRecommendationService
import com.project.byeoldori.observationsites.service.ObservationSiteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Observation Site", description = "관측지 API")
@RestController
@RequestMapping("/observationsites")
class ObservationSiteController(
    private val siteService: ObservationSiteService,
    private val siteRecommendationService: ObservationSiteRecommendationService
) {
    @Operation(summary = "관측지 등록", description = "새로운 관측지 정보를 등록합니다.")
    @PostMapping
    fun create(@RequestBody @Valid dto: ObservationSiteDto): ResponseEntity<ObservationSite> {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.createObservationSite(dto))
    }

    @Operation(summary = "모든 관측지 조회", description = "등록된 모든 관측지 정보를 반환합니다.")
    @GetMapping
    fun getAll(): ResponseEntity<List<ObservationSite>> =
        ResponseEntity.ok(siteService.getAllSites())

    @Operation(summary = "관측지 단건 조회(ID)", description = "하나의 관측지를 조회합니다.")
    @GetMapping("/{id}")
    fun getOne(@PathVariable id: Long): ResponseEntity<ObservationSite> =
        siteService.getById(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @Operation(summary = "관측지 검색", description = "키워드가 포함된 관측지 이름으로 검색합니다.")
    @GetMapping("/name")
    fun searchByName(@RequestParam keyword: String): ResponseEntity<List<ObservationSite>> =
        ResponseEntity.ok(siteService.searchByName(keyword))

    @Operation(summary = "관측지 수정 (ID)")
    @PutMapping("/{id}")
    fun updateById(
        @PathVariable id: Long,
        @RequestBody @Valid dto: ObservationSiteDto
    ): ResponseEntity<ObservationSite> {
        val updated = siteService.updateSiteById(id, dto)
        return updated?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    @Operation(summary = "관측지 삭제 (ID)")
    @DeleteMapping("/{id}")
    fun deleteById(@PathVariable id: Long): ResponseEntity<Void> {
        return if (siteService.deleteSiteById(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    }

    @Operation(summary = "관측지 추천", description = "점수가 높은 상위 5개의 관측지를 추천합니다.")
    @PostMapping("/recommend")
    fun recommendSites(@RequestBody @Valid request: RecommendationRequestDto): ResponseEntity<List<ObservationSiteResponseDto>> {
        val recommended = siteRecommendationService.recommendSites(request.userLat, request.userLon, request.observationTime)
        return ResponseEntity.ok(recommended)
    }
}
