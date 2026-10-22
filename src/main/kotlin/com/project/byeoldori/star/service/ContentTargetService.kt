package com.project.byeoldori.star.service

import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.star.entity.ContentTarget
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.star.repository.ContentTargetRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.Normalizer
import java.util.Locale

// 대상 천체 태그 상한. 라벨마다 content_target 행이 하나씩(IDENTITY 키라 배치 없이 INSERT 한 번씩) 생기므로
// 상한이 없으면 요청 한 번으로 행을 무제한 넣을 수 있다. 교육 프로그램 targets(@Size(max = 30))와 맞춘다.
const val CONTENT_TARGET_MAX_COUNT = 30

// 라벨 길이 상한. star_object_name 은 varchar(255)라 넘으면 저장 단계에서 글 전체가 롤백된다.
const val CONTENT_TARGET_MAX_LENGTH = 100

private val COMBINING_MARKS = Regex("""\p{Mn}+""")

/**
 * 대상 천체 라벨 정리: 앞뒤 공백 제거·빈 값 제거·중복 제거(먼저 나온 표기를 남김).
 * 중복 판단은 DB 정렬(utf8mb4_0900_ai_ci, 대소문자·악센트 무시)과 맞춘다.
 * 'Moon' 과 'moon'(또는 'Vega' 와 'Véga')을 다른 값으로 보면 UNIQUE(content_type, content_id, star_object_name)
 * 에 걸려 글·일정 저장 전체가 409 로 롤백됐다.
 */
fun normalizeTargetLabels(raw: List<String>): List<String> =
    raw.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { targetDedupKey(it) }

internal fun targetDedupKey(label: String): String =
    Normalizer.normalize(label, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

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
        // 앞뒤 공백을 지워 " Moon" 과 "Moon" 이 다른 천체로 저장되지 않게 한다(대소문자·악센트 차이도 같은 천체)
        val uniq = normalizeTargetLabels(targetLabels)
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
