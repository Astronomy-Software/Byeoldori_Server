package com.project.byeoldori.calendar.controller

import com.project.byeoldori.calendar.dto.*
import com.project.byeoldori.calendar.service.CalendarService
import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/calendar")
@Validated
class CalendarController(
    private val service: CalendarService
) {
    @PostMapping("/events")
    @Operation(summary = "계획/기록 작성", description = "status: PLANNED(계획), COMPLETED(완료/기록 작성), CANCELED(계획 취소)")
    fun create(
        @Valid @RequestBody req: CreateEventRequest,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<Long> = ApiResponse.ok(service.create(user, req))

    @GetMapping("/events/date")
    @Operation(summary = "특정 날짜의 계획/기록 목록 조회", description = "YYYY-MM-DD 형식(일별 목록 조회)")
    fun listByDate(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<List<EventResponse>> {
        return ApiResponse.ok(service.listByDate(user, date))
    }

    @GetMapping("/events/{id}")
    @Operation(summary = "단건 상세 조회", description = "하나의 계획/기록 상세 조회")
    fun get(
        @PathVariable id: Long,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<EventResponse> = ApiResponse.ok(service.get(user, id))

    @GetMapping("/events/list")
    @Operation(summary = "목록 조회", description = "기간별 목록 조회(주간/기간별 목록 조회), YYYY-MM-DD")
    fun list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<List<EventResponse>> = ApiResponse.ok(service.list(user, from, to))

    @GetMapping("/events/month")
    @Operation(summary = "월별 요약", description = "한달 계획/기록을 달력에서 점으로 조회")
    fun monthlySummary(
        @RequestParam year: Int,
        @RequestParam month: Int,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<List<DaySummary>> {
        return ApiResponse.ok(service.monthlySummary(user, year, month))
    }

    @PatchMapping("/events/{id}")
    @Operation(summary = "계획 및 기록 수정")
    fun update(
        @PathVariable id: Long,
        @RequestBody req: UpdateEventRequest,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<EventResponse> = ApiResponse.ok(service.update(user, id, req))

    @DeleteMapping("/events/{id}")
    @Operation(summary = "삭제")
    fun delete(
        @PathVariable id: Long,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<Unit> { service.delete(user, id); return ApiResponse.ok() }

    @PostMapping("/events/{id}/images", consumes = ["multipart/form-data"])
    @Operation(summary = "사진 업로드")
    fun uploadPhotos(
        @PathVariable id: Long,
        @RequestParam("files") files: List<MultipartFile>,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<List<PhotoResponse>> = ApiResponse.ok(service.uploadPhotos(user, id, files))

    @PostMapping("/events/{id}/complete")
    @Operation(summary = "계획 -> 완료 처리", description = "계획(PLANNED) → 완료(COMPLETED). observedAt로 종료시간 갱신 가능 " +
            "(생략하면, 계획할 때 적었던 종료 시간 그대로 사용, 그것도 없으면 시작 시간 사용됨")
    fun complete(
        @PathVariable id: Long,
        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        observedAt: LocalDateTime?,
        @RequestAttribute("currentUser") user: User
    ): ApiResponse<EventResponse> = ApiResponse.ok(service.complete(user, id, observedAt))
}