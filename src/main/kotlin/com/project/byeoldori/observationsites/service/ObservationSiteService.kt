package com.project.byeoldori.observationsites.service

import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.community.post.repository.ReviewPostRepository
import com.project.byeoldori.observationsites.dto.ObservationSiteDetailDto
import com.project.byeoldori.observationsites.dto.ObservationSiteDto
import com.project.byeoldori.observationsites.dto.toEntity
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ObservationSiteService(
    private val observationSiteRepository: ObservationSiteRepository,
    private val userSavedSiteRepository: UserSavedSiteRepository,
    private val reviewPostRepository: ReviewPostRepository,
    private val communityPostRepository: CommunityPostRepository
) {

    // 관측지 등록
    fun createObservationSite(dto: ObservationSiteDto): ObservationSite {
        if (observationSiteRepository.existsByName(dto.name)) {
            throw IllegalArgumentException("이미 존재하는 관측지 입니다.")
        }

        return observationSiteRepository.save(dto.toEntity())
    }

    // 모든 관측지 조회
    fun getAllSites(): List<ObservationSite> =
        observationSiteRepository.findAll()

    // 관측지 검색
    fun searchByName(keyword: String): List<ObservationSite> =
        observationSiteRepository.findByNameContaining(keyword)

    fun getById(id: Long): ObservationSite? = observationSiteRepository.findById(id).orElse(null)

    // 상세 정보 조회 메서드
    fun getSiteDetailById(id: Long): ObservationSiteDetailDto? {
        val site = observationSiteRepository.findById(id).orElse(null) ?: return null

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

    // 관측지 수정
    @Transactional
    fun updateSiteById(id: Long, dto: ObservationSiteDto): ObservationSite? {
        val found = observationSiteRepository.findById(id).orElse(null) ?: return null
        val holder = observationSiteRepository.findByName(dto.name)
        if (holder != null && holder.id != id) {
            throw IllegalArgumentException("이미 존재하는 관측지 이름입니다: ${dto.name}")
        }
        val updated = found.copy(name = dto.name, latitude = dto.latitude, longitude = dto.longitude)
        return observationSiteRepository.save(updated)
    }

    // 관측지 삭제
    @Transactional
    fun deleteSiteById(id: Long): Boolean {
        if (!observationSiteRepository.existsById(id)) return false
        userSavedSiteRepository.deleteBySiteId(id)   // 즐겨찾기 정리
        observationSiteRepository.deleteById(id)
        return true
    }
}