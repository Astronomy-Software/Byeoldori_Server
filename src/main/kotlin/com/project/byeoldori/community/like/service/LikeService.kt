package com.project.byeoldori.community.like.service

import com.project.byeoldori.community.like.domain.LikeEntity
import com.project.byeoldori.community.like.dto.LikeToggleResponse
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.user.entity.User
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val postRepository: CommunityPostRepository,
) {

    @Transactional
    fun toggleAndCount(postId: Long, user: User): LikeToggleResponse {
        val post: CommunityPost = postRepository.findById(postId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다. id=$postId") }

        val userId = requireNotNull(user.id) { "user.id must not be null" }

        val existed = likeRepository.existsByPostIdAndUserId(postId, userId)
        if (existed) {
            likeRepository.deleteByPostIdAndUserId(postId, userId)
        } else {
            likeRepository.save(LikeEntity(post = post, user = user))
        }

        val likeCount: Long = likeRepository.countByPostId(postId)
        post.likeCount = likeCount

        return LikeToggleResponse(
            liked = !existed,
            likes = likeCount,
        )
    }
}