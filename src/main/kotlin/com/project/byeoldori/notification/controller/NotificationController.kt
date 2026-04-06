package com.project.byeoldori.notification.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.notification.dto.NotificationResponse
import com.project.byeoldori.notification.dto.toResponse
import com.project.byeoldori.notification.repository.NotificationRepository
import com.project.byeoldori.user.entity.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val notificationRepository: NotificationRepository
) {

    @GetMapping
    fun list(
        @RequestAttribute("currentUser") user: User,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<List<NotificationResponse>>> {
        val pageable = PageRequest.of(maxOf(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.id, pageable)
        return ResponseEntity.ok(ApiResponse.ok(result.content.map { it.toResponse() }))
    }

    @GetMapping("/unread-count")
    fun unreadCount(@RequestAttribute("currentUser") user: User): ResponseEntity<ApiResponse<Long>> {
        val count = notificationRepository.countByUserIdAndIsReadFalse(user.id)
        return ResponseEntity.ok(ApiResponse.ok(count))
    }

    @Transactional
    @PatchMapping("/{id}/read")
    fun markAsRead(
        @RequestAttribute("currentUser") user: User,
        @PathVariable id: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        notificationRepository.markAsRead(id, user.id)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

    @Transactional
    @PatchMapping("/read-all")
    fun markAllAsRead(@RequestAttribute("currentUser") user: User): ResponseEntity<ApiResponse<Unit>> {
        notificationRepository.markAllAsRead(user.id)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }
}
