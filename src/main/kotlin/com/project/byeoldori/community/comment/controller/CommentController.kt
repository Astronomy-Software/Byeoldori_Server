package com.project.byeoldori.community.comment.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.community.comment.dto.CommentCreateRequest
import com.project.byeoldori.community.comment.dto.CommentResponse
import com.project.byeoldori.community.comment.dto.CommentUpdateRequest
import com.project.byeoldori.community.comment.service.CommentService
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.like.dto.LikeToggleResponse
import com.project.byeoldori.community.like.service.LikeService
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
    private val likeService: LikeService,
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
        @RequestParam(defaultValue = "15") size: Int,
        @RequestAttribute("currentUser") user: User
    ): PageResponse<CommentResponse> = service.list(postId, page, size, user)

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

    @PostMapping("/posts/{postId}/comments/{commentId}/likes-toggle")
    @Operation(summary = "댓글 좋아요 토글", description = "댓글에 대한 좋아요를 토글합니다.")
    fun toggleCommentLike(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
        @RequestAttribute("currentUser") user: User
    ): LikeToggleResponse {
        return likeService.toggleCommentLike(postId, commentId, user)
    }

    @PatchMapping("/posts/{postId}/comments/{commentId}")
    @Operation(summary = "댓글 수정", description = "본인이 작성한 댓글 내용을 수정합니다.")
    fun update(
        @PathVariable postId: Long,
        @PathVariable commentId: Long,
        @Valid @RequestBody req: CommentUpdateRequest,
        @RequestAttribute("currentUser") user: User
    ): CommentResponse {
        return service.update(postId, commentId, user, req.content)
    }
}