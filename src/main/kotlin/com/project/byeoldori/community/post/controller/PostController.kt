package com.project.byeoldori.community.post.controller

import com.project.byeoldori.community.common.domain.PostSearchBy
import com.project.byeoldori.community.common.domain.PostSortBy
import com.project.byeoldori.community.like.dto.*
import com.project.byeoldori.community.like.service.LikeService
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.dto.*
import com.project.byeoldori.community.post.service.PostService
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
    @Operation(summary = "타입별 목록 조회", description = "최신순(LATEST), 조회순(VIEWS), 좋아요순(LIKES)으로 정렬 조회")
    fun list(
        @PathVariable type: PostType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "LATEST") sortBy: PostSortBy,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false, defaultValue = "TITLE") searchBy: PostSearchBy
    ): PageResponse<PostSummaryResponse> {

        val sort = Sort.by(Sort.Direction.DESC, sortBy.property)
        val pageable = PageRequest.of(page, size, sort)

        return service.list(type, pageable, searchBy, keyword)
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

    @GetMapping("/home/reviews")
    @Operation(summary = "최신 관측 후기 게시글 홈 화면 조회", description = "커뮤니티 홈의 최신 리뷰 목록을 반환합니다.")
    fun getRecentReviews(): List<PostSummaryResponse> {
        return service.getRecentReviews()
    }

    @GetMapping("/home/educations")
    @Operation(summary = "최신 교육 게시글 홈 화면 조회", description = "커뮤니티 홈의 신규 교육 목록을 반환합니다.")
    fun getNewEducations(): List<PostSummaryResponse> {
        return service.getNewEducations()
    }

    @GetMapping("/home/free-posts")
    @Operation(summary = "인기 자유게시글 목록 조회", description = "커뮤니티 홈의 인기 자유게시글 목록을 반환합니다.")
    fun getPopularFreePosts(): List<PostSummaryResponse> {
        return service.getPopularFreePosts()
    }
}