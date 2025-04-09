package com.project.byeoldori.observation.service

import com.project.byeoldori.observation.dto.ObservationSiteDto
import com.project.byeoldori.observation.dto.toEntity
import com.project.byeoldori.observation.entity.ObservationSite
import com.project.byeoldori.observation.repository.ObservationSiteRepository
import org.springframework.stereotype.Service

@Service
class ObservationSiteService(
    private val observationSiteRepository: ObservationSiteRepository
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

    // 관측지 수정
    fun updateSiteByName(name: String, dto: ObservationSiteDto): ObservationSite? {
        return observationSiteRepository.findByName(name)?.let {
            val updated = it.copy(
                name = dto.name,
                latitude = dto.latitude,
                longitude = dto.longitude
            )
            observationSiteRepository.save(updated)
        }
    }

    // 관측지 삭제
    fun deleteSiteByName(name: String) {
        observationSiteRepository.deleteByName(name)
    }
}