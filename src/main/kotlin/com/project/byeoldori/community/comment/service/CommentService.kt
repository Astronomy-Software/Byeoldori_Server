package com.project.byeoldori.community.comment.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.community.comment.dto.CommentResponse
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.like.repository.CommentLikeRepository
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.user.entity.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService(
    private val postRepo: CommunityPostRepository,
    private val commentRepo: CommentRepository,
    private val commentLikeRepo: CommentLikeRepository
) {

    @Transactional
    fun write(postId: Long, author: User, content: String, parentId: Long? = null): CommentResponse {
        val post = postRepo.findById(postId).orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND) }

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

        post.commentCount++

        return savedComment.toResponse(isLiked = false)
    }

    @Transactional(readOnly = true)
    fun list(postId: Long, page: Int, size: Int, user: User?): PageResponse<CommentResponse> {
        require(page > 0) { "페이지 번호는 1 이상이어야 합니다." }

        val sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        val pageable = PageRequest.of(page - 1, size, sort)
        val result = commentRepo.findByPostId(postId, pageable)

        val commentIds = result.content.mapNotNull { it.id }
        val likedCommentIds = if (user != null && commentIds.isNotEmpty()) {
            commentLikeRepo.findLikedCommentIds(user.id, commentIds).toSet()
        } else {
            emptySet()
        }

        return PageResponse(
            content = result.content.map { it.toResponse(likedCommentIds.contains(it.id)) }, // <-- [수정]
            page = page,
            size = size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional
    fun delete(postId: Long, commentId: Long, requester: User) {
        val c = commentRepo.findById(commentId).orElseThrow { NotFoundException(ErrorCode.COMMENT_NOT_FOUND) }
        if (c.post.id != postId) throw InvalidInputException("댓글이 해당 게시글에 속하지 않습니다.")
        if (c.author.id != requester.id) throw ForbiddenException("본인 댓글만 삭제할 수 있습니다.")
        if (!c.deleted) {
            c.deleted = true
            c.content = "삭제된 댓글입니다."
            if (c.post.commentCount > 0) {
                c.post.commentCount--
            }
        }
    }

    @Transactional
    fun update(postId: Long, commentId: Long, requester: User, content: String): CommentResponse {
        val comment = commentRepo.findById(commentId).orElseThrow {
            NotFoundException(ErrorCode.COMMENT_NOT_FOUND)
        }

        if (comment.post.id != postId) {
            throw InvalidInputException(ErrorCode.INVALID_COMMENT_FOR_POST.message)
        }

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

    private fun Comment.toResponse(isLiked: Boolean) = CommentResponse(
        id = this.id!!,
        authorId = this.author.id,
        authorNickname = this.author.nickname,
        content = if (this.deleted) "삭제된 댓글입니다." else this.content,
        createdAt = this.createdAt,
        parentId = this.parent?.id,
        depth = this.depth,
        deleted = this.deleted,
        likeCount = this.likeCount,
        liked = isLiked
    )
}