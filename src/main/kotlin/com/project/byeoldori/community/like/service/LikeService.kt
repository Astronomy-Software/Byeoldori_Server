package com.project.byeoldori.community.like.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.like.dto.LikeToggleResponse
import com.project.byeoldori.community.like.repository.CommentLikeRepository
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.notification.service.NotificationService
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.utils.NicknameRules
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val commentRepository: CommentRepository,
    private val postRepository: CommunityPostRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val notificationService: NotificationService
) {

    @Transactional
    fun toggleAndCount(postId: Long, user: User): LikeToggleResponse {
        if (!postRepository.existsById(postId)) throw NotFoundException(ErrorCode.POST_NOT_FOUND)

        // 같은 사용자의 요청이 동시에 와도(더블클릭) PK 중복 500 이 나지 않도록 INSERT IGNORE·벌크 삭제를 쓰고,
        // 실제로 바뀐 행 수만큼만 카운터를 원자적으로 움직인다(엔티티 필드 대입은 다른 카운터를 덮어쓴다).
        val existed = likeRepository.existsByPostIdAndUserId(postId, user.id)
        val delta = if (existed) {
            -likeRepository.deleteByPostIdAndUserId(postId, user.id)
        } else {
            likeRepository.insertIgnore(postId, user.id)
        }
        if (delta != 0) postRepository.addLikeCount(postId, delta.toLong())

        val likeCount: Long = postRepository.findLikeCountById(postId) ?: 0

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

        // 게시글 좋아요와 같은 방식 — 동시 요청에도 PK 중복이 나지 않고 카운터는 원자적으로 움직인다
        val existed = commentLikeRepository.existsByCommentIdAndUserId(commentId, user.id)
        val delta = if (existed) {
            -commentLikeRepository.deleteByCommentIdAndUserId(commentId, user.id)
        } else {
            commentLikeRepository.insertIgnore(commentId, user.id, LocalDateTime.now())
        }
        if (delta != 0) commentRepository.addLikeCount(commentId, delta.toLong())

        val likeCount: Long = commentRepository.findLikeCountById(commentId) ?: 0

        // 좋아요를 실제로 추가했을 때만 댓글 작성자에게 알림 (본인 아닐 때, 겹친 요청은 한 번만)
        if (delta > 0 && comment.author.id != user.id) {
            notificationService.notifyCommentLiked(
                commentAuthorId = comment.author.id,
                likerName = user.nickname ?: NicknameRules.FALLBACK_DISPLAY_NAME
            )
        }

        return LikeToggleResponse(
            liked = !existed,
            likes = likeCount,
        )
    }
}