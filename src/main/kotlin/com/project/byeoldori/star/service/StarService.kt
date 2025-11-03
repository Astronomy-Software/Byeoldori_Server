package com.project.byeoldori.star.service

import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.star.dto.StarSummaryResponse
import com.project.byeoldori.star.entity.Star
import com.project.byeoldori.star.repository.StarRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.repository.findByIdOrNull

@Service
@Transactional(readOnly = true)
class StarService(
    private val starRepo: StarRepository
) {
    fun getSummary(objectName: String): StarSummaryResponse {
        val s: Star = starRepo.findByIdOrNull(objectName)
            ?: throw NotFoundException(ErrorCode.STAR_NOT_FOUND)
        return StarSummaryResponse(s.objectName, s.reviewCount, s.educationCount)
    }

    fun createIfAbsentExact(rawName: String): Star {
        val name: String = rawName.trim()
        val existing: Star? = starRepo.findByIdOrNull(name)
        if (existing != null) return existing

        return try {
            starRepo.save(Star(objectName = name))
        } catch (e: DataIntegrityViolationException) {
            starRepo.findByIdOrNull(name) ?: throw e
        }
    }

    @Transactional
    fun getOrCreateSummary(objectName: String): StarSummaryResponse {
        val s: Star = createIfAbsentExact(objectName)
        return StarSummaryResponse(s.objectName, s.reviewCount, s.educationCount)
    }

    fun validateExistAll(names: Collection<String>) {
        if (names.isEmpty()) return
        val found: Set<String> = starRepo.findAllById(names).map { it.objectName }.toSet()
        val missing: List<String> = names.filterNot(found::contains)
        if (missing.isNotEmpty()) throw NotFoundException(ErrorCode.STAR_NOT_FOUND)
    }

    fun findExistingExact(rawName: String?): Star? {
        val name = rawName?.trim().orEmpty()
        if (name.isEmpty()) return null
        return starRepo.findByIdOrNull(name)
    }

    @Transactional
    fun increseReview(names: List<String>) {
        if (names.isNotEmpty()) starRepo.incReview(names)
    }

    @Transactional
    fun decreseReview(names: List<String>) {
        if (names.isNotEmpty()) starRepo.decReview(names)
    }

    @Transactional
    fun increseEducation(names: List<String>) {
        if (names.isNotEmpty()) starRepo.incEducation(names)
    }

    @Transactional
    fun decreseEducation(names: List<String>) {
        if (names.isNotEmpty()) starRepo.decEducation(names)
    }
}