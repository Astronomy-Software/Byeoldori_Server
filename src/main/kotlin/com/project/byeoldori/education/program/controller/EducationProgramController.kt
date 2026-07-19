package com.project.byeoldori.education.program.controller

import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.education.program.dto.CreateProgramRequest
import com.project.byeoldori.education.program.dto.ProgramDetailResponse
import com.project.byeoldori.education.program.dto.ProgramSummaryResponse
import com.project.byeoldori.education.program.dto.UpdateProgramRequest
import com.project.byeoldori.education.program.service.EducationProgramService
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/education/programs")
@Tag(name = "EducationProgram", description = "교육 프로그램(인터랙티브 씬) API")
class EducationProgramController(
    private val service: EducationProgramService
) {
    @PostMapping
    @Operation(summary = "교육 프로그램 생성", description = "DRAFT 상태로 생성합니다. 작성자=현재 사용자.")
    fun create(
        @Valid @RequestBody req: CreateProgramRequest,
        @RequestAttribute("currentUser") user: User
    ): ProgramDetailResponse = service.create(user, req)

    @GetMapping
    @Operation(
        summary = "교육 프로그램 목록",
        description = "기본: PUBLISHED 공개 목록. mine=true: 내 전체 상태. pending=true: 검수 큐(PREVIEW, 관리자 전용)."
    )
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false, defaultValue = "false") mine: Boolean,
        @RequestParam(required = false, defaultValue = "false") pending: Boolean,
        // 비로그인 공개 목록(PUBLISHED)을 허용하므로 nullable. mine/pending 은 인증 필요.
        @RequestAttribute(value = "currentUser", required = false) user: User?
    ): PageResponse<ProgramSummaryResponse> {
        // 비로그인도 호출 가능한 공개 API 라 상한이 없으면 ?size=1000000 한 방으로
        // 전체 프로그램(steps 포함)을 메모리에 올리게 된다. 반드시 제한한다.
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        return when {
            pending -> service.listPending(requireLogin(user), pageable)
            mine -> service.listMine(requireLogin(user), pageable)
            else -> service.listPublished(pageable)
        }
    }

    private fun requireLogin(user: User?): User =
        user ?: throw ForbiddenException("로그인이 필요합니다.")

    @GetMapping("/{id}")
    @Operation(summary = "교육 프로그램 상세", description = "PUBLISHED 는 누구나, DRAFT/PREVIEW 는 작성자 또는 관리자만.")
    fun get(
        @PathVariable id: String,
        // PUBLISHED 는 비로그인도 조회 가능하므로 nullable
        @RequestAttribute(value = "currentUser", required = false) user: User?
    ): ProgramDetailResponse = service.get(id, user)

    @PatchMapping("/{id}")
    @Operation(summary = "교육 프로그램 수정", description = "작성자 본인 & DRAFT/PREVIEW 상태에서만.")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody req: UpdateProgramRequest,
        @RequestAttribute("currentUser") user: User
    ): ProgramDetailResponse = service.update(id, req, user)

    @PostMapping("/{id}/submit")
    @Operation(summary = "검수 요청", description = "DRAFT → PREVIEW (작성자 본인).")
    fun submit(
        @PathVariable id: String,
        @RequestAttribute("currentUser") user: User
    ): ProgramDetailResponse = service.submit(id, user)

    @PostMapping("/{id}/publish")
    @Operation(summary = "발행", description = "PREVIEW → PUBLISHED. moderation.required 가 true 면 관리자만, false 면 작성자도 가능.")
    fun publish(
        @PathVariable id: String,
        @RequestAttribute("currentUser") user: User
    ): ProgramDetailResponse = service.publish(id, user)

    @PostMapping("/{id}/reject")
    @Operation(summary = "반려", description = "PREVIEW → DRAFT (관리자 전용).")
    fun reject(
        @PathVariable id: String,
        @RequestAttribute("currentUser") user: User
    ): ProgramDetailResponse = service.reject(id, user)

    @DeleteMapping("/{id}")
    @Operation(summary = "교육 프로그램 삭제", description = "작성자 또는 관리자.")
    fun delete(
        @PathVariable id: String,
        @RequestAttribute("currentUser") user: User
    ): ResponseEntity<ApiResponse<Unit>> {
        service.delete(id, user)
        return ResponseEntity.ok(ApiResponse.ok("교육 프로그램이 삭제되었습니다."))
    }

    @PostMapping("/{id}/view")
    @Operation(summary = "조회수 증가")
    fun incrementView(
        @PathVariable id: String,
        @Suppress("UNUSED_PARAMETER")
        @RequestAttribute(value = "currentUser", required = false) user: User?
    ): ResponseEntity<ApiResponse<Unit>> {
        service.incrementView(id)
        return ResponseEntity.ok(ApiResponse.ok())
    }
}
