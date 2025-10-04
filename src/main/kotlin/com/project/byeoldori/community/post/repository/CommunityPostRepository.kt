package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommunityPostRepository : JpaRepository<CommunityPost, Long> {
    fun findAllByType(type: PostType, pageable: Pageable): Page<CommunityPost>

    fun findByTypeAndTitleContaining(type: PostType, title: String, pageable: Pageable): Page<CommunityPost>
    fun findByTypeAndContentContaining(type: PostType, content: String, pageable: Pageable): Page<CommunityPost>
    fun findByTypeAndAuthorNicknameContaining(type: PostType, nickname: String, pageable: Pageable): Page<CommunityPost>

    @Query("SELECT SUM(p.likeCount) FROM ReviewPost r JOIN r.post p WHERE r.observationSite.id = :siteId")
    fun sumLikesBySiteId(@Param("siteId") siteId: Long): Long?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommunityPost p set p.viewCount = p.viewCount + 1 where p.id = :postId")
    fun increaseViewCount(@Param("postId") postId: Long): Int
}