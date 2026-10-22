package com.project.byeoldori.community.like.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.like.dto.LikeToggleResponse
import com.project.byeoldori.community.like.repository.CommentLikeRepository
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.notification.service.NotificationService
import com.project.byeoldori.user.entity.User
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
class LikeService(
    private val likeRepository: LikeRepository,
    private val commentRepository: CommentRepository,
    private val postRepository: CommunityPostRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val notificationService: NotificationService,
    private val redisTemplate: StringRedisTemplate,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        // 같은 사람이 같은 댓글에 좋아요를 취소했다 다시 눌러도 이 시간 안에는 알림을 한 번만 보낸다
        internal val COMMENT_LIKE_NOTIFY_TTL: Duration = Duration.ofHours(24)

        internal fun commentLikeNotifyKey(commentId: Long, likerId: Long) = "notify:comment-like:$commentId:$likerId"
    }

    /**
     * 댓글 좋아요 알림을 보내도 되는지 — (댓글, 누른 사람)마다 TTL 동안 한 번만.
     * 좋아요·취소를 반복하면 매번 새 좋아요라 알림이 계속 쌓였다(알림함 도배·notifications 행 증가).
     * Redis 장애 시에는 예전처럼 보낸다(좋아요 자체가 실패하지 않게).
     */
    private fun firstCommentLikeNotify(commentId: Long, likerId: Long): Boolean = try {
        redisTemplate.opsForValue()
            .setIfAbsent(commentLikeNotifyKey(commentId, likerId), "1", COMMENT_LIKE_NOTIFY_TTL) != false
    } catch (e: Exception) {
        logger.warn("댓글 좋아요 알림 중복 확인 실패, 알림을 보냅니다: {}", e.message)
        true
    }

    @Transactional
    fun toggleAndCount(postId: Long, user: User): LikeToggleResponse {
        // 부모 게시글 행을 먼저 X 락으로 잡아 다른 사용자와 동시에 눌러도 데드락이 나지 않게 한다
        postRepository.findByIdForUpdate(postId) ?: throw NotFoundException(ErrorCode.POST_NOT_FOUND)

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
        // 부모 댓글 행을 먼저 X 락으로 잡는다(게시글 좋아요와 같은 데드락 방지)
        val comment = commentRepository.findByIdForUpdate(commentId)
            ?: throw NotFoundException(ErrorCode.COMMENT_NOT_FOUND)

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

        // 좋아요를 실제로 추가했을 때만 댓글 작성자에게 알림 (본인 아닐 때, 겹친 요청은 한 번만,
        // 취소 후 다시 누른 좋아요는 TTL 안에서 다시 알리지 않음)
        if (delta > 0 && comment.author.id != user.id && firstCommentLikeNotify(commentId, user.id)) {
            notificationService.notifyCommentLiked(
                commentAuthorId = comment.author.id,
                likerName = user.nickname ?: user.name
            )
        }

        return LikeToggleResponse(
            liked = !existed,
            likes = likeCount,
        )
    }
}