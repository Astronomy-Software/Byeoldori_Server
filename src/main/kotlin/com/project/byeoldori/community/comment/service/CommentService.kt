package com.project.byeoldori.community.comment.service

import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.community.comment.dto.CommentResponse
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.user.entity.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.transaction.annotation.Transactional

@Service
class CommentService(
    private val postRepo: CommunityPostRepository,
    private val commentRepo: CommentRepository
) {

    @Transactional
    fun write(postId: Long, author: User, content: String, parentId: Long? = null): Long {
        val post = postRepo.findById(postId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.")
        }

        val parent = parentId?.let {
            val p = commentRepo.findById(it).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.")
            }
            if (p.post.id != postId) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글이 다른 게시글에 속합니다.")
            }
            p
        }

        val depth = if (parent != null) 1 else 0
        val saved = commentRepo.save(
            Comment(post = post, author = author, content = content, parent = parent, depth = depth)
        )

        // 카운트 동기화
        post.commentCount = commentRepo.countByPostIdAndDeletedFalse(postId)
        return saved.id!!
    }

    @Transactional(readOnly = true)
    fun list(postId: Long, page: Int, size: Int): PageResponse<CommentResponse> {
        // 정렬은 서비스에서만 관리: createdAt ASC, id ASC
        val sort = Sort.by(
            Sort.Order.asc("createdAt"),
            Sort.Order.asc("id")
        )
        val pageable = PageRequest.of(page, size, sort)

        val result = commentRepo.findByPostId(postId, pageable)

        return PageResponse(
            content = result.content.map {
                CommentResponse(
                    id = it.id!!,
                    authorId = it.author.id,
                    content = it.content,
                    createdAt = it.createdAt!!,
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
        }
        c.post.commentCount = commentRepo.countByPostIdAndDeletedFalse(c.post.id!!)
    }
}