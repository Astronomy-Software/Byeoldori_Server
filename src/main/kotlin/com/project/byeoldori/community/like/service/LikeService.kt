package com.project.byeoldori.community.like.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.like.domain.CommentLike
import com.project.byeoldori.community.like.domain.LikeEntity
import com.project.byeoldori.community.like.dto.LikeToggleResponse
import com.project.byeoldori.community.like.repository.CommentLikeRepository
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val commentRepository: CommentRepository,
    private val postRepository: CommunityPostRepository,
    private val commentLikeRepository: CommentLikeRepository,
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

    @Transactional
    fun toggleCommentLike(postId: Long, commentId: Long, user: User): LikeToggleResponse {
        val comment = commentRepository.findById(commentId)
            .orElseThrow { NotFoundException(ErrorCode.COMMENT_NOT_FOUND) }

        if (comment.deleted) {
            throw InvalidInputException(ErrorCode.CANNOT_LIKE_DELETED_COMMENT.message)
        }

        if (comment.post.id != postId) {
            throw InvalidInputException(ErrorCode.INVALID_COMMENT_FOR_POST.message)
        }

        val existed = commentLikeRepository.existsByCommentIdAndUserId(commentId, user.id)
        if (existed) {
            commentLikeRepository.deleteByCommentIdAndUserId(commentId, user.id)
        } else {
            commentLikeRepository.save(CommentLike(comment = comment, user = user))
        }

        val likeCount = commentLikeRepository.countByCommentId(commentId)
        comment.likeCount = likeCount

        return LikeToggleResponse(
            liked = !existed,
            likes = likeCount,
        )
    }
}