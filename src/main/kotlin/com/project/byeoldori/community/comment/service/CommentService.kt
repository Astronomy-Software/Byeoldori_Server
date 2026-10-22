package com.project.byeoldori.community.comment.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.community.comment.dto.CommentResponse
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.like.repository.CommentLikeRepository
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.notification.service.NotificationService
import com.project.byeoldori.user.entity.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService(
    private val postRepo: CommunityPostRepository,
    private val commentRepo: CommentRepository,
    private val commentLikeRepo: CommentLikeRepository,
    private val notificationService: NotificationService
) {

    @Transactional
    fun write(postId: Long, author: User, content: String, parentId: Long? = null): CommentResponse {
        // 댓글 INSERT(FK S 락) 뒤 카운터 UPDATE(X 락) 순서라 동시 작성·좋아요와 데드락이 날 수 있어 게시글 행을 먼저 X 락으로 잡는다
        val post = postRepo.findByIdForUpdate(postId) ?: throw NotFoundException(ErrorCode.POST_NOT_FOUND)

        val parent = parentId?.takeIf { it > 0 }?.let { validParentId ->
            val p = commentRepo.findById(validParentId)
                .orElseThrow { NotFoundException(ErrorCode.COMMENT_NOT_FOUND, "답글을 작성할 부모 댓글을 찾을 수 없습니다.") }
            if (p.post.id != postId) throw InvalidInputException(ErrorCode.INVALID_PARENT_COMMENT.message)
            if (p.depth > 0) throw InvalidInputException(ErrorCode.CANNOT_REPLY_TO_REPLY.message)
            p
        }

        val depth = if (parent != null) 1 else 0
        val savedComment = commentRepo.save(
            Comment(post = post, author = author, content = content, parent = parent, depth = depth)
        )

        // 원자적 증가 — post.commentCount++ 는 행 전체를 옛 값으로 다시 써서 동시에 달린 댓글·좋아요·조회수를 덮어쓴다
        postRepo.addCommentCount(postId, 1)

        // 본인 게시글이 아닌 경우 작성자에게 알림 발송
        if (post.author.id != author.id) {
            notificationService.notifyNewComment(
                postAuthorId = post.author.id,
                commenterName = author.nickname ?: author.name,
                preview = content.take(30)
            )
        }

        return savedComment.toResponse(isLiked = false)
    }

    @Transactional(readOnly = true)
    fun list(postId: Long, page: Int, size: Int, user: User?): PageResponse<CommentResponse> {
        // require 는 IllegalArgumentException(→500)이라 범위 밖 값은 거부 대신 보정한다(size 상한 포함).
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)

        val sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        val pageable = PageRequest.of(safePage, safeSize, sort)
        val result = commentRepo.findByPostId(postId, pageable)

        val commentIds = result.content.mapNotNull { it.id }
        val likedCommentIds = if (user != null && commentIds.isNotEmpty()) {
            commentLikeRepo.findLikedCommentIds(user.id, commentIds).toSet()
        } else {
            emptySet()
        }

        return PageResponse(
            content = result.content.map { it.toResponse(likedCommentIds.contains(it.id)) }, // <-- [수정]
            page = safePage,
            size = safeSize,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional
    fun delete(postId: Long, commentId: Long, requester: User) {
        // 잠금 없이 읽으면 같은 댓글 DELETE 가 겹칠 때 둘 다 deleted=false 를 보고 comment_count 를 두 번 줄인다.
        // 댓글 행을 X 락으로 읽어 두 번째 요청은 앞 요청 커밋 뒤의 deleted=true 를 보게 한다.
        val c = commentRepo.findByIdForUpdate(commentId) ?: throw NotFoundException(ErrorCode.COMMENT_NOT_FOUND)
        validateCommentBelongsToPost(c, postId)
        if (c.author.id != requester.id) throw ForbiddenException("본인 댓글만 삭제할 수 있습니다.")
        if (!c.deleted) {
            c.deleted = true
            c.content = "삭제된 댓글입니다."
            // 원자적 감소(0 아래로는 내려가지 않음)
            postRepo.addCommentCount(postId, -1)
        }
    }

    @Transactional
    fun update(postId: Long, commentId: Long, requester: User, content: String): CommentResponse {
        val comment = commentRepo.findById(commentId).orElseThrow {
            NotFoundException(ErrorCode.COMMENT_NOT_FOUND)
        }

        validateCommentBelongsToPost(comment, postId)

        if (comment.deleted) {
            throw NotFoundException(ErrorCode.COMMENT_NOT_FOUND)
        }

        if (comment.author.id != requester.id) {
            throw ForbiddenException(ErrorCode.OWN_COMMENT_ONLY.message)
        }

        val newContent = content.trim()
        if (newContent.isBlank()) {
            throw InvalidInputException(ErrorCode.COMMENT_CONTENT_EMPTY.message)
        }
        if (newContent != comment.content) {
            comment.content = newContent
        }

        val isLiked = commentLikeRepo.existsByCommentIdAndUserId(comment.id!!, requester.id)
        return comment.toResponse(isLiked)
    }

    private fun validateCommentBelongsToPost(comment: Comment, postId: Long) {
        if (comment.post.id != postId) throw InvalidInputException(ErrorCode.INVALID_COMMENT_FOR_POST.message)
    }

    private fun Comment.toResponse(isLiked: Boolean) = CommentResponse(
        id = this.id!!, authorId = this.author.id, authorNickname = this.author.nickname,
        authorProfileImageUrl = this.author.profileImageUrl, content = if (this.deleted) "삭제된 댓글입니다." else this.content,
        createdAt = this.createdAt, parentId = this.parent?.id, depth = this.depth, deleted = this.deleted,
        likeCount = this.likeCount, liked = isLiked
    )
}