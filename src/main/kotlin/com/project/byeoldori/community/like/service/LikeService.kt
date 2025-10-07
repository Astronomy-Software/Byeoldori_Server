package com.project.byeoldori.community.like.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.like.domain.LikeEntity
import com.project.byeoldori.community.like.dto.LikeToggleResponse
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val postRepository: CommunityPostRepository,
) {

    @Transactional
    fun toggleAndCount(postId: Long, user: User): LikeToggleResponse {
        val post: CommunityPost = postRepository.findById(postId)
            .orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND) }

        val existed = likeRepository.existsByPostIdAndUserId(postId, user.id)
        if (existed) {
            likeRepository.deleteByPostIdAndUserId(postId, user.id)
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