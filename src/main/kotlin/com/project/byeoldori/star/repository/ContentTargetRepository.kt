package com.project.byeoldori.star.repository

import com.project.byeoldori.star.entity.ContentTarget
import com.project.byeoldori.star.entity.ContentType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ContentTargetRepository : JpaRepository<ContentTarget, Long> {

    @Modifying
    @Query("delete from ContentTarget ct where ct.contentType = :type and ct.contentId = :id")
    fun deleteAllByContent(
        @Param("type") type: ContentType,
        @Param("id") id: Long
    ): Int

    @Query("""select ct.contentId from ContentTarget ct where ct.contentType = :type and ct.starObjectName = :name""")
    fun findContentIdsByStar(
        @Param("type") type: ContentType,
        @Param("name") name: String,
        pageable: Pageable
    ): Page<Long>

    fun findAllByContentTypeAndContentId(
        type: ContentType,
        contentId: Long
    ): List<ContentTarget>
}