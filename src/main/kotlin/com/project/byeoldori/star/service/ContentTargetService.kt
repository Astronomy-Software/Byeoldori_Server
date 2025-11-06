package com.project.byeoldori.star.service

import com.project.byeoldori.star.entity.ContentTarget
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.star.repository.ContentTargetRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentTargetService(
    private val targetRepo: ContentTargetRepository
) {
    @Transactional
    fun upsertTargets(
        type: ContentType,
        contentId: Long,
        targetLabels: List<String>
    ) {
        targetRepo.deleteAllByContent(type, contentId)

        val uniq = LinkedHashSet(targetLabels.filter { it.isNotBlank() })
        var i = 0
        val entities = uniq.map { label ->
            ContentTarget(
                contentType = type,
                contentId = contentId,
                starObjectName = label,
                sortOrder = i++
            )
        }
        if (entities.isNotEmpty()) targetRepo.saveAll(entities)
    }

    @Transactional(readOnly = true)
    fun findContentIdsByStar(
        type: ContentType,
        starObjectName: String,
        pageable: Pageable
    ): Page<Long> = targetRepo.findContentIdsByStar(type, starObjectName, pageable)

    @Transactional(readOnly = true)
    fun listTargetsOf(type: ContentType, contentId: Long): List<ContentTarget> =
        targetRepo.findAllByContentTypeAndContentId(type, contentId)
}