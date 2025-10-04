package com.project.byeoldori.community.comment.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.community.comment.dto.CommentCreateRequest
import com.project.byeoldori.community.comment.dto.CommentResponse
import com.project.byeoldori.community.comment.service.CommentService
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/community")
@Validated
@Tag(name = "Comment", description = "커뮤니티 기능 댓글 관련 API")
class CommentController(
    private val service: CommentService,
) {
    @PostMapping("/posts/{postId}/comments")
    @Operation(summary = "댓글/대댓글 작성", description = "댓글/대댓글을 작성합니다.")
    fun create(
        @PathVariable postId: Long,
        @Valid @RequestBody req: CommentCreateRequest,
        @RequestAttribute("currentUser") user: User
    ): CommentResponse {
        return service.write(postId, user, req.content, req.parentId)
    }

    @GetMapping("/posts/{postId}/comments")
    @Operation(summary = "댓글 목록", description = "작성 시각 오름차순으로 페이징 조회합니다.")
    fun list(
        @PathVariable postId: Long,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "15") size: Int
    ): PageResponse<CommentResponse> = service.list(postId, page, size)

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    @Operation(summary = "댓글 삭제", description = "작성자만 삭제 가능합니다.")
    fun delete(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
        @RequestAttribute("currentUser") user: User
    ): ResponseEntity<ApiResponse<Unit>> {
        service.delete(postId, commentId, user)
        return ResponseEntity.ok(ApiResponse.ok("댓글이 삭제되었습니다."))
    }
}