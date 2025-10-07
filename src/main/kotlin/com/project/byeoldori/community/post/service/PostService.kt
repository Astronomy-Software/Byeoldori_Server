package com.project.byeoldori.community.post.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.common.domain.*
import com.project.byeoldori.community.common.dto.*
import com.project.byeoldori.community.post.domain.*
import com.project.byeoldori.community.post.dto.*
import com.project.byeoldori.community.post.repository.*
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.user.entity.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional
class PostService(
    private val postRepo: CommunityPostRepository,
    private val reviewRepo: ReviewPostRepository,
    private val eduRepo: EducationPostRepository,
    private val imgRepo: PostImageRepository,
    private val freePostRepo: FreePostRepository,
    private val siteRepo: ObservationSiteRepository
) {

    @Value("\${community.home.item-count:4}")
    private val homeItemCount: Int = 4

    @Transactional
    fun create(author: User, type: PostType, req: PostCreateRequest): Long {
        val post = postRepo.save(
            CommunityPost(author = author, type = type, title = req.title, content = req.content)
        )

        when (type) {
            PostType.REVIEW -> {
                val d = req.review ?: ReviewDto()
                val site = d.observationSiteId?.let {
                    siteRepo.findById(it).orElse(null)
                }
                reviewRepo.save(
                    ReviewPost(
                        post = post,
                        observationSite = site,
                        location = d.location,
                        target = d.target,
                        equipment = d.equipment,
                        observationDate = d.observationDate?.let(LocalDate::parse),
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
                        status = d.status ?: EducationStatus.DRAFT
                    )
                )
            }
            PostType.FREE -> {
                freePostRepo.save(FreePost(post = post))
            }
        }

        req.imageUrls.orEmpty().forEachIndexed { idx, url ->
            imgRepo.save(PostImage(post = post, url = url, sortOrder = idx))
        }
        return post.id!!
    }

    @Transactional(readOnly = true)
    fun getRecentReviews(): List<PostSummaryResponse> {
        return postRepo.findAllByType(
            PostType.REVIEW,
            PageRequest.of(0, homeItemCount, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).content.map { it.toSummaryResponse() }
    }

    @Transactional(readOnly = true)
    fun getNewEducations(): List<PostSummaryResponse> {
        return postRepo.findAllByType(
            PostType.EDUCATION,
            PageRequest.of(0, homeItemCount, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).content.map { it.toSummaryResponse() }
    }

    @Transactional(readOnly = true)
    fun getPopularFreePosts(): List<PostSummaryResponse> {
        return postRepo.findAllByType(
            PostType.FREE,
            PageRequest.of(0, homeItemCount, Sort.by(Sort.Direction.DESC, "viewCount"))
        ).content.map { it.toSummaryResponse() }
    }

    @Transactional(readOnly = true)
    fun list(type: PostType, pageable: Pageable, searchBy: PostSearchBy, keyword: String?): PageResponse<PostSummaryResponse> { // <- 수정

        val postPage = if (keyword.isNullOrBlank()) {
            postRepo.findAllByType(type, pageable)
        } else {
            when (searchBy) {
                PostSearchBy.TITLE -> postRepo.findByTypeAndTitleContaining(type, keyword, pageable)
                PostSearchBy.CONTENT -> postRepo.findByTypeAndContentContaining(type, keyword, pageable)
                PostSearchBy.NICKNAME -> postRepo.findByTypeAndAuthorNicknameContaining(type, keyword, pageable)
            }
        }

        return postPage.map { it.toSummaryResponse() }.toPageResponse()
    }

    @Transactional
    fun detail(postId: Long): PostResponse {
        val updated = postRepo.increaseViewCount(postId)
        if (updated == 0) throw NotFoundException(ErrorCode.POST_NOT_FOUND)

        val p = postRepo.findById(postId).orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND) }
        val images = imgRepo.findAllByPostIdOrderBySortOrderAsc(postId).map { it.url }

        val review = reviewRepo.findById(postId).orElse(null)?.let {
            ReviewDto(it.location, it.observationSite?.id, it.target, it.equipment, it.observationDate?.toString(), it.score)
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
            createdAt = p.createdAt,
            updatedAt = p.updatedAt
        )
    }

    @Transactional
    fun update(postId: Long, req: PostUpdateRequest, user: User) {
        val p = postRepo.findById(postId).orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND) }
        if (p.author.id != user.id) throw ForbiddenException()

        req.title?.let { p.title = it }
        req.content?.let { p.content = it }

        when (p.type) {
            PostType.REVIEW -> req.review?.let { updateReviewPost(postId, it) }
            PostType.EDUCATION -> req.education?.let { updateEducationPost(postId, it) }
            PostType.FREE -> { }
        }
        req.imageUrls?.let { requestedUrls ->
            updatePostImages(p, requestedUrls)
        }
    }

    @Transactional
    fun delete(postId: Long, user: User) {
        val p = postRepo.findById(postId).orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND) }
        if (p.author.id != user.id) throw ForbiddenException()
        postRepo.delete(p)
    }

    private fun updateReviewPost(postId: Long, reviewDto: ReviewDto) {
        val reviewPost = reviewRepo.findById(postId)
            .orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND, "수정할 리뷰 정보를 찾을 수 없습니다.") }

        reviewDto.location?.let { reviewPost.location = it }
        reviewDto.target?.let { reviewPost.target = it }
        reviewDto.equipment?.let { reviewPost.equipment = it }
        reviewDto.observationDate?.let { reviewPost.observationDate = LocalDate.parse(it) }
        reviewDto.score?.let { reviewPost.score = it }

        if (reviewDto.observationSiteId != null) {
            val site = siteRepo.findById(reviewDto.observationSiteId)
                .orElseThrow { NotFoundException(ErrorCode.SITE_NOT_FOUND, "연결할 관측지를 찾을 수 없습니다.") }
            reviewPost.observationSite = site
        } else {
            reviewPost.observationSite = null
        }
    }

    private fun updateEducationPost(postId: Long, educationDto: EducationDto) {
        val educationPost = eduRepo.findById(postId)
            .orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND, "수정할 교육 정보를 찾을 수 없습니다.") }

        educationDto.summary?.let { educationPost.summary = it }
        educationDto.difficulty?.let { educationPost.difficulty = it }
        educationDto.tags?.let { educationPost.tags = it }
        educationDto.status?.let { educationPost.status = it }
    }

    private fun updatePostImages(post: CommunityPost, requestedUrls: List<String>) {
        val existingImages = imgRepo.findAllByPostIdOrderBySortOrderAsc(post.id!!)
        val existingImageMap = existingImages.associateBy { it.url }

        val requestedUrlSet = requestedUrls.toSet()
        val imagesToDelete = existingImages.filter { it.url !in requestedUrlSet }
        if (imagesToDelete.isNotEmpty()) {
            imgRepo.deleteAll(imagesToDelete)
        }

        val imagesToAdd = mutableListOf<PostImage>()
        requestedUrls.forEachIndexed { newOrder, url ->
            val existingImage = existingImageMap[url]
            if (existingImage == null) {
                // Case 1: 새로 추가된 이미지 -> Add 리스트에 추가
                imagesToAdd.add(PostImage(post = post, url = url, sortOrder = newOrder))
            } else {
                // Case 2: 기존에 있던 이미지 -> 순서(sortOrder)가 바뀌었는지 확인하고 업데이트
                if (existingImage.sortOrder != newOrder) {
                    existingImage.sortOrder = newOrder
                }
            }
        }
        if (imagesToAdd.isNotEmpty()) {
            imgRepo.saveAll(imagesToAdd)
        }
    }

    private fun CommunityPost.toSummaryResponse(): PostSummaryResponse {
        val summary = if (this.type == PostType.FREE) {
            this.content.take(30)
        } else {
            null
        }

        return PostSummaryResponse(
            id = this.id!!, type = this.type, title = this.title, authorId = this.author.id, authorNickname = this.author.nickname,
            contentSummary = summary, viewCount = this.viewCount, likeCount = this.likeCount, commentCount = this.commentCount,
            createdAt = this.createdAt
        )
    }
}