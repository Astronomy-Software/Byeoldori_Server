package com.project.byeoldori.community.like.controller

import com.project.byeoldori.community.like.dto.LikeToggleResponse
import com.project.byeoldori.community.like.service.LikeService
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/community")
@Tag(name = "Like", description = "커뮤니티 좋아요 API")
class LikeController(
    private val service: LikeService
) {
    @PostMapping("/posts/{postId}/likes/toggle")
    @Operation(summary = "좋아요 토글", description = "이미 좋아요면 취소, 아니면 좋아요")
    fun toggle(
        @PathVariable postId: Long,
        @RequestAttribute("currentUser") user: User
    ): LikeToggleResponse {
        return service.toggleAndCount(postId, user)
    }
}