package com.project.byeoldori.observationsites.service

import com.project.byeoldori.calendar.repository.ObservationEventRepository
import com.project.byeoldori.common.exception.*
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.community.post.repository.ReviewPostRepository
import com.project.byeoldori.observationsites.dto.*
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Service

@Service
class ObservationSiteService(
    private val observationSiteRepository: ObservationSiteRepository,
    private val userSavedSiteRepository: UserSavedSiteRepository,
    private val reviewPostRepository: ReviewPostRepository,
    private val communityPostRepository: CommunityPostRepository,
    private val observationEventRepository: ObservationEventRepository
) {

    // 관측지 등록
    fun createObservationSite(dto: ObservationSiteDto): ObservationSiteResponseDto {
        if (observationSiteRepository.existsByName(dto.name)) {
            throw ConflictException(ErrorCode.SITE_NAME_ALREADY_EXISTS)
        }
        return observationSiteRepository.save(dto.toEntity()).toResponseDto()
    }

    // 모든 관측지 조회 (페이지네이션)
    @Transactional(readOnly = true)
    fun getAllSites(pageable: Pageable): Page<ObservationSiteResponseDto> {
        val sitePage = observationSiteRepository.findAll(pageable)
        val siteIds = sitePage.content.map { it.id }
        val scoreMap: Map<Long, Double> = if (siteIds.isEmpty()) emptyMap()
        else reviewPostRepository.findAverageScoresBySiteIds(siteIds)
            .associate { row -> (row[0] as Long) to (row[1] as Double) }
        return sitePage.map { site ->
            ObservationSiteResponseDto(
                id = site.id,
                name = site.name,
                latitude = site.latitude,
                longitude = site.longitude,
                averageScore = scoreMap[site.id]
            )
        }
    }

    // 관측지 검색 (LIKE 부분 일치).
    // 예전에는 2글자 이상이면 MATCH ... AGAINST(FULLTEXT)를 썼지만 observation_site.name 에 FULLTEXT 인덱스가
    // 없어(V1~V3) 운영에서 ER_FT_MATCHING_KEY_NOT_FOUND → 500 이 났다. 관측지는 수십 건이라 LIKE 로 충분하다.
    // Containing 은 Spring Data 가 % _ 를 이스케이프하므로 검색어의 와일드카드가 그대로 글자로 취급된다.
    @Transactional(readOnly = true)
    fun searchByName(keyword: String): List<ObservationSiteResponseDto> =
        observationSiteRepository.findByNameContaining(keyword.trim()).map { it.toResponseDto() }

    // 상세 정보 조회 메서드
    @Transactional(readOnly = true)
    fun getSiteDetailById(id: Long): ObservationSiteDetailDto {
        val site = observationSiteRepository.findById(id)
            .orElseThrow { NotFoundException(ErrorCode.SITE_NOT_FOUND) }
        val reviewCount = reviewPostRepository.countByObservationSiteId(id)
        val totalLikes = communityPostRepository.sumLikesBySiteId(id) ?: 0L
        val averageScore = reviewPostRepository.findAverageScoreBySiteId(id) ?: 0.0

        return ObservationSiteDetailDto(
            id = site.id,
            name = site.name,
            latitude = site.latitude,
            longitude = site.longitude,
            reviewCount = reviewCount,
            totalLikes = totalLikes,
            averageScore = averageScore
        )
    }

    // 관측지 업데이트
    @Transactional
    fun updateSiteById(id: Long, dto: ObservationSiteDto): ObservationSiteResponseDto {
        val found = observationSiteRepository.findById(id)
            .orElseThrow { NotFoundException(ErrorCode.SITE_NOT_FOUND) }
        val holder = observationSiteRepository.findByName(dto.name)
        if (holder != null && holder.id != id) {
            throw ConflictException(ErrorCode.SITE_NAME_ALREADY_EXISTS)
        }
        val updated = found.copy(name = dto.name, latitude = dto.latitude, longitude = dto.longitude)
        return observationSiteRepository.save(updated).toResponseDto()
    }

    // 관측지 삭제
    @Transactional
    fun deleteSiteById(id: Long) {
        val site = observationSiteRepository.findById(id)
            .orElseThrow { NotFoundException(ErrorCode.SITE_NOT_FOUND) }
        userSavedSiteRepository.deleteAllBySite_Id(id)
        // 후기·캘린더 기록이 이 관측지를 FK 로 참조하면 삭제가 실패(500)하므로 먼저 연결을 끊는다.
        reviewPostRepository.detachObservationSite(id, site.name)
        observationEventRepository.detachObservationSite(id)
        observationSiteRepository.deleteById(id)
    }
}