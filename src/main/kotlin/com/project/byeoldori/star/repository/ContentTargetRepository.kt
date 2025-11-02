package com.project.byeoldori.star.repository

import com.project.byeoldori.star.entity.ContentTarget
import com.project.byeoldori.star.entity.ContentType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface ContentTargetRepository : JpaRepository<ContentTarget, Long> {
    @Modifying
    @Query("delete from ContentTarget ct where ct.contentType = :type and ct.contentId = :id")
    fun deleteAllByContent(type: ContentType, id: Long): Int

    @Query("""select ct.contentId from ContentTarget ct where ct.contentType = :type and ct.starObjectName = :name""")
    fun findContentIdsByStar(
        type: ContentType,
        name: String,
        pageable: Pageable
    ): Page<Long>

    fun findAllByContentTypeAndContentId(type: ContentType, contentId: Long): List<ContentTarget>

    @Query("""select ct from ContentTarget ct where ct.contentType = :type and ct.contentId in :ids""")
    fun findAllByTypeAndContentIds(type: ContentType, ids: Collection<Long>): List<ContentTarget>
}