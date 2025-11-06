package com.project.byeoldori.community.post.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.common.domain.*
import com.project.byeoldori.community.common.dto.*
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.community.post.domain.*
import com.project.byeoldori.community.post.dto.*
import com.project.byeoldori.community.post.repository.*
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.star.service.ContentTargetService
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.user.entity.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Transactional
class PostService(
    private val postRepo: CommunityPostRepository,
    private val reviewRepo: ReviewPostRepository,
    private val eduRepo: EducationPostRepository,
    private val imgRepo: PostImageRepository,
    private val freePostRepo: FreePostRepository,
    private val siteRepo: ObservationSiteRepository,
    private val likeRepository: LikeRepository,
    private val educationRatingRepo: EducationRatingRepository,
    private val storage: StorageService,
    private val contentTargetService: ContentTargetService,
) {

    @Value("\${community.home.item-count:20}")
    private val homeItemCount: Int = 20

    @Transactional
    fun create(author: User, type: PostType, req: PostCreateRequest): Long {
        val post = postRepo.save(
            CommunityPost(author = author, type = type, title = req.title, content = req.content)
        )

        when (type) {
            PostType.REVIEW -> {
                val d = req.review ?: ReviewDto()

                val site = d.observationSiteId?.let { siteRepo.findById(it).orElse(null) }

                reviewRepo.save(
                    ReviewPost(
                        post = post,
                        observationSite = site,
                        location = d.location,
                        equipment = d.equipment,
                        observationDate = d.observationDate,
                        score = d.score
                    )
                )

                contentTargetService.upsertTargets(
                    ContentType.REVIEW,
                    post.id!!,
                    d.targets?: emptyList()
                )
            }

            PostType.EDUCATION -> {
                val d = req.education ?: EducationRequestDto()

                eduRepo.save(
                    EducationPost(
                        post = post,
                        difficulty = d.difficulty,
                        tags = d.tags,
                        status = d.status ?: EducationStatus.DRAFT,
                    )
                )

                contentTargetService.upsertTargets(
                    ContentType.EDUCATION,
                    post.id!!,
                    d.targets ?: emptyList(),
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
    fun getRecentReviews(user: User? = null): List<PostSummaryResponse> {
        val pageable = PageRequest.of(0, homeItemCount, Sort.by(Sort.Direction.DESC, "createdAt"))
        val posts = postRepo.findAllByType(PostType.REVIEW, pageable)
        return mapToSummaryResponse(posts.content, user)
    }

    @Transactional(readOnly = true)
    fun getNewEducations(user: User? = null): List<PostSummaryResponse> {
        val pageable = PageRequest.of(0, homeItemCount, Sort.by(Sort.Direction.DESC, "createdAt"))
        val posts = postRepo.findAllByType(PostType.EDUCATION, pageable)
        return mapToSummaryResponse(posts.content, user)
    }

    @Transactional(readOnly = true)
    fun getPopularFreePosts(user: User? = null): List<PostSummaryResponse> {
        val pageable = PageRequest.of(0, homeItemCount, Sort.by(Sort.Direction.DESC, "viewCount"))
        val posts = postRepo.findAllByType(PostType.FREE, pageable)
        return mapToSummaryResponse(posts.content, user)
    }

    @Transactional(readOnly = true)
    fun list(
        type: PostType,
        pageable: Pageable,
        searchBy: PostSearchBy,
        keyword: String?,
        user: User? = null
    ): PageResponse<PostSummaryResponse> {

        val postPage = if (keyword.isNullOrBlank()) {
            postRepo.findAllByType(type, pageable)
        } else {
            when (searchBy) {
                PostSearchBy.TITLE    -> postRepo.findByTypeAndTitleContaining(type, keyword, pageable)
                PostSearchBy.CONTENT  -> postRepo.findByTypeAndContentContaining(type, keyword, pageable)
                PostSearchBy.NICKNAME -> postRepo.findByTypeAndAuthorNicknameContaining(type, keyword, pageable)
            }
        }

        val summaryList = mapToSummaryResponse(postPage.content, user)
        return PageImpl(summaryList, postPage.pageable, postPage.totalElements).toPageResponse()
    }

    @Transactional(readOnly = true)
    fun listAllByStar(
        type: PostType,
        starObjectName: String,
        user: User? = null
    ): List<PostSummaryResponse> {

        val ids: List<Long> = when (type) {
            PostType.REVIEW -> contentTargetService
                .findContentIdsByStar(ContentType.REVIEW, starObjectName, Pageable.unpaged())
                .content
            PostType.EDUCATION -> contentTargetService
                .findContentIdsByStar(ContentType.EDUCATION, starObjectName, Pageable.unpaged())
                .content
            PostType.FREE -> emptyList()
        }
        if (ids.isEmpty()) return emptyList()

        val posts = postRepo.findAllById(ids)
            .filter { it.type == type }
            .sortedByDescending { it.createdAt }

        return mapToSummaryResponse(posts, user)
    }

    @Transactional
    fun detail(postId: Long, user: User? = null): PostResponse {
        val updated = postRepo.increaseViewCount(postId)
        if (updated == 0) throw NotFoundException(ErrorCode.POST_NOT_FOUND)

        val p = postRepo.findById(postId).orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND) }
        val images = imgRepo.findAllByPostIdOrderBySortOrderAsc(postId).map { it.url }

        val review = reviewRepo.findById(postId).orElse(null)?.let {
            val targets: List<String> = contentTargetService
                .listTargetsOf(ContentType.REVIEW, postId)
                .sortedBy { t -> t.sortOrder }
                .map { t -> t.starObjectName }
                .filter { it.isNotBlank() }

            ReviewDto(
                location = it.location,
                observationSiteId = it.observationSite?.id,
                targets = targets,
                equipment = it.equipment,
                observationDate = it.observationDate,
                score = it.score
            )
        }
        val education = eduRepo.findById(postId).orElse(null)?.let { ep ->
            val targets = contentTargetService
                .listTargetsOf(ContentType.EDUCATION, postId)
                .sortedBy { t -> t.sortOrder }
                .map { t -> t.starObjectName }
                .filter { it.isNotBlank() }
            EducationResponseDto(
                difficulty = ep.difficulty,
                targets = targets, tags = ep.tags, status = ep.status, averageScore = ep.averageScore
            )
        }


        val liked = if (user != null) likeRepository.existsByPostIdAndUserId(postId, user.id) else false

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
            updatedAt = p.updatedAt,
            liked = liked
        )
    }

    @Transactional
    fun update(postId: Long, req: PostUpdateRequest, user: User) {
        val p = postRepo.findById(postId).orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND) }
        if (p.author.id != user.id) throw ForbiddenException()

        req.title?.let { p.title = it }
        req.content?.let { p.content = it }

        when (p.type) {
            PostType.REVIEW    -> req.review?.let { updateReviewPost(postId, it) }
            PostType.EDUCATION -> req.education?.let { updateEducationPost(postId, it) }
            PostType.FREE -> { }
        }

        req.imageUrls?.let { requestedUrls ->
            updatePostImages(p, requestedUrls)
        }

        when (p.type) {
            PostType.REVIEW -> {
                req.review?.let { r ->
                    contentTargetService.upsertTargets(
                        ContentType.REVIEW,
                        postId,
                        r.targets ?: emptyList()
                    )
                }
            }
            PostType.EDUCATION -> {
                req.education?.let { e ->
                    contentTargetService.upsertTargets(
                        ContentType.EDUCATION,
                        postId,
                        e.targets ?: emptyList()
                    )
                }
            }
            else -> {}
        }
    }

    @Transactional
    fun delete(postId: Long, user: User) {
        val p = postRepo.findById(postId).orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND) }
        if (p.author.id != user.id) throw ForbiddenException()

        when (p.type) {
            PostType.REVIEW -> {
                contentTargetService.upsertTargets(ContentType.REVIEW, postId, emptyList())
            }
            PostType.EDUCATION -> {
                contentTargetService.upsertTargets(ContentType.EDUCATION, postId, emptyList())
            }
            else -> {}
        }

        val images = imgRepo.findAllByPostIdOrderBySortOrderAsc(p.id!!)
        val urls = images.map { it.url }

        if (images.isNotEmpty()) imgRepo.deleteAll(images)
        postRepo.delete(p)

        if (urls.isNotEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    urls.forEach { url ->
                        try { storage.deleteImageByUrl(url) } catch (_: Exception) { }
                    }
                }
            })
        }
    }

    private fun updateReviewPost(postId: Long, reviewDto: ReviewDto) {
        val reviewPost = reviewRepo.findById(postId)
            .orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND, "수정할 리뷰 정보를 찾을 수 없습니다.") }

        reviewDto.location?.let { reviewPost.location = it }
        reviewDto.equipment?.let { reviewPost.equipment = it }
        reviewDto.observationDate?.let { reviewPost.observationDate = it }
        reviewDto.score?.let { reviewPost.score = it }

        if (reviewDto.observationSiteId != null) {
            val site = siteRepo.findById(reviewDto.observationSiteId)
                .orElseThrow { NotFoundException(ErrorCode.SITE_NOT_FOUND, "연결할 관측지를 찾을 수 없습니다.") }
            reviewPost.observationSite = site
        } else {
            reviewPost.observationSite = null
        }
    }

    private fun updateEducationPost(postId: Long, educationDto: EducationRequestDto) {
        val educationPost = eduRepo.findById(postId)
            .orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND, "수정할 교육 정보를 찾을 수 없습니다.") }

        educationDto.difficulty?.let { educationPost.difficulty = it }
        educationDto.tags?.let { educationPost.tags = it }
        educationDto.status?.let { educationPost.status = it }
    }

    private fun updatePostImages(post: CommunityPost, requestedUrls: List<String>) {
        val existingImages = imgRepo.findAllByPostIdOrderBySortOrderAsc(post.id!!)
        val existingImageMap = existingImages.associateBy { it.url }

        val requestedUrlSet = requestedUrls.toSet()
        val imagesToDelete = existingImages.filter { it.url !in requestedUrlSet }
        val urlsToDelete = imagesToDelete.map { it.url }

        if (imagesToDelete.isNotEmpty()) {
            imgRepo.deleteAll(imagesToDelete)
        }

        val imagesToAdd = mutableListOf<PostImage>()
        requestedUrls.forEachIndexed { newOrder, url ->
            val existingImage = existingImageMap[url]
            if (existingImage == null) {
                imagesToAdd.add(PostImage(post = post, url = url, sortOrder = newOrder))
            } else if (existingImage.sortOrder != newOrder) {
                existingImage.sortOrder = newOrder
            }
        }
        if (imagesToAdd.isNotEmpty()) {
            imgRepo.saveAll(imagesToAdd)
        }
        if (urlsToDelete.isNotEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    urlsToDelete.forEach { url ->
                        try { storage.deleteImageByUrl(url) } catch (_: Exception) { }
                    }
                }
            })
        }
    }

    private fun likedSet(user: User?, postIds: List<Long>): Set<Long> {
        if (user == null || postIds.isEmpty()) return emptySet()
        return likeRepository.findLikedPostIds(user.id, postIds).toSet()
    }

    private fun CommunityPost.toSummaryResponse(
        observationSiteId: Long? = null,
        liked: Boolean = false,
        score: Double? = 0.0,
        thumbnailUrl: String? = null
    ): PostSummaryResponse {
        val summary = if (this.type == PostType.FREE) this.content.take(30) else null

        return PostSummaryResponse(
            id = this.id!!, type = this.type, title = this.title, authorId = this.author.id, authorNickname = this.author.nickname,
            observationSiteId = observationSiteId, contentSummary = summary, viewCount = this.viewCount, likeCount = this.likeCount,
            commentCount = this.commentCount, createdAt = this.createdAt, liked = liked, score = score, thumbnailUrl = thumbnailUrl
        )
    }

    @Transactional
    fun rateEducationPost(postId: Long, user: User, score: Int) {
        val eduPost = eduRepo.findById(postId)
            .orElseThrow { NotFoundException(ErrorCode.POST_NOT_FOUND, "평가할 교육 게시글을 찾을 수 없습니다.") }

        if (eduPost.post.author.id == user.id) {
            throw ForbiddenException("자신이 작성한 글에는 평점을 매길 수 없습니다.")
        }
        val rating = educationRatingRepo.findById(EducationRatingId(postId, user.id)).orElse(null)

        if (rating != null) {
            rating.score = score
        } else {
            educationRatingRepo.save(EducationRating(educationPost = eduPost, user = user, score = score))
            eduPost.ratingCount++
        }

        eduPost.averageScore = educationRatingRepo.findAverageScoreByPostId(postId)
    }

    private fun mapToSummaryResponse(posts: List<CommunityPost>, user: User?): List<PostSummaryResponse> {
        if (posts.isEmpty()) return emptyList()

        val postIds = posts.mapNotNull { it.id }
        val likedPostIds = likedSet(user, postIds)

        val postType = posts.first().type
        val scoresMap = when (postType) {
            PostType.REVIEW -> reviewRepo.findAllById(postIds).associate { it.id to (it.score?.toDouble() ?: 0.0) }
            PostType.EDUCATION -> eduRepo.findAllById(postIds).associate { it.id to it.averageScore }
            else -> emptyMap()
        }

        val observationSiteIdMap = if (postType == PostType.REVIEW) {
            reviewRepo.findObservationSiteIdsByPostIds(postIds)
                .associate { (postId, siteId) -> (postId as Long) to (siteId as Long) }
        } else {
            emptyMap()
        }

        val thumbnailsMap = mutableMapOf<Long, String>()
        imgRepo.findByPostIdInOrderByPostIdAscSortOrderAsc(postIds).forEach { img ->
            val pid = img.post.id!!
            if (!thumbnailsMap.containsKey(pid)) thumbnailsMap[pid] = img.url
        }

        return posts.map { post ->
            post.toSummaryResponse(
                observationSiteId = observationSiteIdMap[post.id],
                liked = likedPostIds.contains(post.id),
                score = scoresMap[post.id] ?: 0.0,
                thumbnailUrl = thumbnailsMap[post.id!!]
            )
        }
    }
}