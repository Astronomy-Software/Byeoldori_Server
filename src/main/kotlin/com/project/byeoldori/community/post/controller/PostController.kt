package com.project.byeoldori.community.post.controller

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
    private val service: PostService
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

    @PostMapping("/posts/{postId}/views")
    @Operation(summary = "조회수 증가")
    fun increaseView(@PathVariable postId: Long) = service.increaseView(postId)
}