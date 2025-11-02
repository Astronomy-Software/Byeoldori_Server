package com.project.byeoldori.star.service

import com.project.byeoldori.star.entity.ContentTarget
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.star.repository.ContentTargetRepository
import com.project.byeoldori.star.repository.StarRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentTargetService(
    private val targetRepo: ContentTargetRepository,
    private val starRepo: StarRepository,
    private val starService: StarService,
) {
    private fun normalize(raw: List<String>): List<String> = raw
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    @Transactional
    fun upsertTargets(
        type: ContentType,
        contentId: Long,
        rawTargets: List<String>,
        adjustStarCounts: Boolean = true,
    ): Set<String> {
        val inputs = normalize(rawTargets)

        val oldLinks = targetRepo.findAllByContentTypeAndContentId(type, contentId)
        val oldStars = oldLinks.mapNotNull { it.starObjectName }.toSet()

        targetRepo.deleteAllByContent(type, contentId)

        val newStars = linkedSetOf<String>()
        var order = 0
        for (t in inputs) {
            val star = starRepo.findByObjectName(t)
            if (star != null) {
                targetRepo.save(
                    ContentTarget(
                        contentType = type,
                        contentId = contentId,
                        starObjectName = star.objectName,
                        freeText = null,
                        sortOrder = order++,
                    )
                )
                newStars += star.objectName
            } else {
                targetRepo.save(
                    ContentTarget(
                        contentType = type,
                        contentId = contentId,
                        starObjectName = null,
                        freeText = t,
                        sortOrder = order++,
                    )
                )
            }
        }

        if (adjustStarCounts) adjustCounts(type, oldStars, newStars)
        return newStars
    }

    private fun adjustCounts(type: ContentType, oldStars: Set<String>, newStars: Set<String>) {
        val toInc = newStars - oldStars
        val toDec = oldStars - newStars
        when (type) {
            ContentType.REVIEW -> {
                if (toInc.isNotEmpty()) starService.increseReview(toInc.toList())
                if (toDec.isNotEmpty()) starService.decreseReview(toDec.toList())
            }
            ContentType.EDUCATION -> {
                if (toInc.isNotEmpty()) starService.increseEducation(toInc.toList())
                if (toDec.isNotEmpty()) starService.decreseEducation(toDec.toList())
            }
            ContentType.EVENT -> { }
        }
    }

    fun findContentIdsByStar(type: ContentType, starObjectName: String, pageable: Pageable): Page<Long> =
        targetRepo.findContentIdsByStar(type, starObjectName, pageable)

    fun listTargetsOf(type: ContentType, contentId: Long): List<ContentTarget> =
        targetRepo.findAllByContentTypeAndContentId(type, contentId)
}