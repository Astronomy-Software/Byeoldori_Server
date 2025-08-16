package com.project.byeoldori.community.post.service

import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.*
import com.project.byeoldori.community.post.dto.*
import com.project.byeoldori.community.post.repository.*
import com.project.byeoldori.user.entity.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@Service
class PostService(
    private val postRepo: CommunityPostRepository,
    private val reviewRepo: ReviewPostRepository,
    private val eduRepo: EducationPostRepository,
    private val imgRepo: PostImageRepository
) {
    @Transactional
    fun create(author: User, type: PostType, req: PostCreateRequest): Long {
        val post = postRepo.save(
            CommunityPost(author = author, type = type, title = req.title, content = req.content)
        )

        when (type) {
            PostType.REVIEW -> {
                val d = req.review ?: ReviewDto()
                reviewRepo.save(
                    ReviewPost(
                        post = post,
                        location = d.location,
                        target = d.target,
                        equipment = d.equipment,
                        observationDate = d.observationDate?.let(LocalDateTime::parse),
                        score = d.score
                    )
                )
            }
            PostType.EDUCATION -> {
                val d = req.education ?: EducationDto()
                eduRepo.save(
                    EducationPost(
                        post = post,
                        summary = d.summary,
                        difficulty = d.difficulty,
                        tags = d.tags,
                        status = d.status ?: com.project.byeoldori.community.common.domain.EducationStatus.DRAFT
                    )
                )
            }
            PostType.FREE -> {
                // FREE는 디테일 테이블 없이 community만 사용
            }
        }

        req.imageUrls.orEmpty().forEachIndexed { idx, url ->
            imgRepo.save(PostImage(post = post, url = url, sortOrder = idx))
        }
        return post.id!!
    }

    @Transactional(readOnly = true)
    fun list(type: PostType, pageable: Pageable): Page<CommunityPost> =
        postRepo.findAllByType(type, pageable)

    @Transactional(readOnly = true)
    fun detail(postId: Long): PostResponse {
        val p = postRepo.findById(postId).orElseThrow()
        val images = imgRepo.findAllByPostIdOrderBySortOrderAsc(postId).map { it.url }

        val review = reviewRepo.findById(postId).orElse(null)?.let {
            ReviewDto(it.location, it.target, it.equipment, it.observationDate?.toString(), it.score)
        }
        val education = eduRepo.findById(postId).orElse(null)?.let {
            EducationDto(it.summary, it.difficulty, it.tags, it.status)
        }

        return PostResponse(
            id = p.id!!,
            type = p.type,
            title = p.title,
            content = p.content,
            authorId = p.author.id,
            images = images,
            review = review,
            education = education,
            viewCount = p.viewCount,
            likeCount = p.likeCount,
            commentCount = p.commentCount,
            createdAt = p.createdAt?.toString(),
            updatedAt = p.updatedAt?.toString()
        )
    }

    @Transactional
    fun update(postId: Long, req: PostUpdateRequest, user: User) {
        val p = postRepo.findById(postId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.")
        }
        if (p.author.id != user.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음")
        }

        req.title?.let { p.title = it }
        req.content?.let { p.content = it }
    }

    @Transactional
    fun delete(postId: Long, user: User) {
        val p = postRepo.findById(postId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.")
        }
        if (p.author.id != user.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음")
        }
        postRepo.delete(p)
    }

    @Transactional fun increaseView(postId: Long) { postRepo.increaseViewCount(postId) }
}