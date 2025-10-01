package com.project.byeoldori.community.comment.service

import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.community.comment.dto.CommentResponse
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.user.entity.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CommentService(
    private val postRepo: CommunityPostRepository,
    private val commentRepo: CommentRepository
) {

    @Transactional
    fun write(postId: Long, author: User, content: String, parentId: Long? = null): CommentResponse {
        val post = postRepo.findById(postId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.")
        }

        val parent = parentId?.takeIf { it > 0 }?.let { validParentId ->
            val p = commentRepo.findById(validParentId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "답글을 작성할 부모 댓글을 찾을 수 없습니다.")
            }
            if (p.post.id != postId) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "부모 댓글이 다른 게시글에 속해있습니다.")
            }
            if (p.depth > 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "더 이상 답글을 작성할 수 없습니다.")
            }
            p
        }

        val depth = if (parent != null) 1 else 0
        val savedComment = commentRepo.save(
            Comment(post = post, author = author, content = content, parent = parent, depth = depth)
        )

        post.commentCount++

        return CommentResponse(
            id = savedComment.id!!,
            authorId = savedComment.author.id,
            content = savedComment.content,
            createdAt = savedComment.createdAt,
            parentId = savedComment.parent?.id,
            depth = savedComment.depth,
            deleted = savedComment.deleted
        )
    }

    @Transactional(readOnly = true)
    fun list(postId: Long, page: Int, size: Int): PageResponse<CommentResponse> {
        require(page > 0) { "페이지 번호는 1 이상이어야 합니다." }

        val sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        val pageable = PageRequest.of(page - 1, size, sort)
        val result = commentRepo.findByPostId(postId, pageable)

        return PageResponse(
            content = result.content.map {
                CommentResponse(
                    id = it.id!!,
                    authorId = it.author.id,
                    content = it.content,
                    createdAt = it.createdAt,
                    parentId = it.parent?.id,
                    depth = it.depth,
                    deleted = it.deleted
                )
            },
            page = page,
            size = size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional
    fun delete(postId: Long, commentId: Long, requester: User) {
        val c = commentRepo.findById(commentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.")
        }
        if (c.post.id != postId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글이 해당 게시글에 속하지 않습니다.")
        }
        if (c.author.id != requester.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "본인 댓글만 삭제할 수 있습니다.")
        }
        if (!c.deleted) {
            c.deleted = true
            c.content = "삭제된 댓글입니다."

            if (c.post.commentCount > 0) {
                c.post.commentCount--
            }
        }
    }
}