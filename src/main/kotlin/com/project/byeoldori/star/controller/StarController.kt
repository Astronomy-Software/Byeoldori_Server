package com.project.byeoldori.star.controller

import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.service.PostService
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@Tag(name = "Star")
@RestController
@RequestMapping("/stars")
class StarController(
    private val postService: PostService
) {
    @GetMapping("/{objectName}/reviews")
    @Operation(summary = "해당 천체 관측 후기글 조회", description = "해당 천체의 관측 후기 게시글을 조회합니다.")
    fun getReviewsByStar(
        @PathVariable objectName: String,
        @RequestAttribute(name = "currentUser", required = false) user: User?
    ) = ApiResponse.ok(postService.listAllByStar(PostType.REVIEW, objectName, user))

    @GetMapping("/{objectName}/educations")
    @Operation(summary = "해당 천체 교육 프로그램글 조회", description = "해당 천체의 교육 프로그램 게시글을 조회합니다.")
    fun getEducationsByStar(
        @PathVariable objectName: String,
        @RequestAttribute(name = "currentUser", required = false) user: User?
    ) = ApiResponse.ok(postService.listAllByStar(PostType.EDUCATION, objectName, user))
}