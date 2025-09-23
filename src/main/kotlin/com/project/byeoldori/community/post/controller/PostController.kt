package com.project.byeoldori.community.post.controller

import com.project.byeoldori.community.like.dto.*
import com.project.byeoldori.community.like.service.LikeService
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.dto.*
import com.project.byeoldori.community.post.service.PostService
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.common.dto.toPageResponse
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/community")
@Tag(name = "Post", description = "커뮤니티 게시글 API")
class PostController(
    private val service: PostService,
    private val likeService: LikeService
) {
    @PostMapping("/{type}/posts")
    @Operation(summary = "게시글 생성")
    fun create(
        @PathVariable type: PostType,
        @Valid @RequestBody req: PostCreateRequest,
        @RequestAttribute("currentUser") user: User
    ): IdResponse = IdResponse(service.create(user, type, req))

    @GetMapping("/{type}/posts")
    @Operation(summary = "타입별 목록 조회")
    fun list(@PathVariable type: PostType, pageable: Pageable): PageResponse<PostSummaryResponse> {
        val pageData = service.list(type, pageable).map {
            PostSummaryResponse(
                id = it.id!!, type = it.type, title = it.title, authorId = it.author.id,
                viewCount = it.viewCount, likeCount = it.likeCount, commentCount = it.commentCount,
                createdAt = it.createdAt?.toString()
            )
        }
        return pageData.toPageResponse()
    }

    @GetMapping("/posts/{postId}")
    @Operation(summary = "상세 조회")
    fun detail(@PathVariable postId: Long): PostResponse = service.detail(postId)

    @PatchMapping("/posts/{postId}")
    @Operation(summary = "수정")
    fun update(
        @PathVariable postId: Long,
        @RequestBody req: PostUpdateRequest,
        @RequestAttribute("currentUser") user: User
    ) = service.update(postId, req, user)

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "삭제")
    fun delete(
        @PathVariable postId: Long,
        @RequestAttribute("currentUser") user: User
    ) = service.delete(postId, user)

    @PostMapping("/posts/{postId}/likes/toggle")
    @Operation(summary = "좋아요 토글", description = "이미 좋아요면 취소, 아니면 좋아요")
    fun toggleLike(
        @PathVariable postId: Long,
        @RequestAttribute("currentUser") user: User
    ): LikeToggleResponse = likeService.toggleAndCount(postId, user)

    @PutMapping("/posts/{postId}/likes")
    @Operation(summary = "좋아요 설정(멱등)", description = "결과를 항상 좋아요 상태로 맞춥니다")
    fun like(
        @PathVariable postId: Long,
        @RequestAttribute("currentUser") user: User
    ): LikeToggleResponse = likeService.ensureLike(postId, user)

    @DeleteMapping("/posts/{postId}/likes")
    @Operation(summary = "좋아요 해제(멱등)", description = "결과를 항상 좋아요 취소 상태로 맞춥니다")
    fun unlike(
        @PathVariable postId: Long,
        @RequestAttribute("currentUser") user: User
    ): LikeToggleResponse = likeService.ensureUnlike(postId, user)

    @GetMapping("/posts/{postId}/likes/count")
    @Operation(summary = "좋아요 수 조회", description = "해당 게시글의 총 좋아요 수")
    fun likeCount(
        @PathVariable postId: Long
    ): LikeCountResponse = LikeCountResponse(likeService.count(postId))
}