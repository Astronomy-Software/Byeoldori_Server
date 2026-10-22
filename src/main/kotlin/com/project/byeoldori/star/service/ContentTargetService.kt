package com.project.byeoldori.star.service

import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.star.entity.ContentTarget
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.star.repository.ContentTargetRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 대상 천체 태그 상한. 라벨마다 content_target 행이 하나씩(IDENTITY 키라 배치 없이 INSERT 한 번씩) 생기므로
// 상한이 없으면 요청 한 번으로 행을 무제한 넣을 수 있다. 교육 프로그램 targets(@Size(max = 30))와 맞춘다.
const val CONTENT_TARGET_MAX_COUNT = 30

// 라벨 길이 상한. star_object_name 은 varchar(255)라 넘으면 저장 단계에서 글 전체가 롤백된다.
const val CONTENT_TARGET_MAX_LENGTH = 100

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
        // 앞뒤 공백을 지워 " Moon" 과 "Moon" 이 다른 천체로 저장되지 않게 한다
        val uniq = LinkedHashSet(targetLabels.map { it.trim() }.filter { it.isNotEmpty() })
        if (uniq.size > CONTENT_TARGET_MAX_COUNT) {
            throw InvalidInputException("대상 천체는 최대 ${CONTENT_TARGET_MAX_COUNT}개입니다.")
        }
        if (uniq.any { it.length > CONTENT_TARGET_MAX_LENGTH }) {
            throw InvalidInputException("대상 천체 이름은 ${CONTENT_TARGET_MAX_LENGTH}자를 넘을 수 없습니다.")
        }

        targetRepo.deleteAllByContent(type, contentId)

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
