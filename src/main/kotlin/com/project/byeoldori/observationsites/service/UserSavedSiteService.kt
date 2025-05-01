package com.project.byeoldori.observationsites.service

import com.project.byeoldori.observationsites.dto.ObservationSiteDto
import com.project.byeoldori.observationsites.dto.toSimpleDto
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class UserSavedSiteService(
    private val userSavedSiteRepository: UserSavedSiteRepository,
    private val observationSiteRepository: ObservationSiteRepository
) {

    // 추천된 관측지를 사용자 저장 목록에 추가
    fun saveSite(userId: String, siteId: Long): Boolean {
        // 관측지 유효성 확인
        if (!observationSiteRepository.existsById(siteId)) {
            throw IllegalArgumentException("해당 관측지는 존재하지 않습니다.")
        }

        // 중복 저장 방지
        if (userSavedSiteRepository.existsByUserIdAndSiteId(userId, siteId)) {
            return false // 이미 저장된 경우 저장하지 않음
        }

        val saved = UserSavedSite(
            userId = userId,
            siteId = siteId,
            savedAt = LocalDateTime.now()
        )
        userSavedSiteRepository.save(saved)
        return true
    }

    // 저장된 관측지 정보를 반환
    fun getSavedSitesWithInfo(userId: String): List<ObservationSiteDto> {
        val savedSiteIds = userSavedSiteRepository.findByUserId(userId).map { it.siteId }
        val sites = observationSiteRepository.findAllById(savedSiteIds)
        return sites.map { it.toSimpleDto() }
    }

    // 지도에서 검색 or 선택하여 관측지 저장
    fun saveCustomSite(userId: String, dto: ObservationSiteDto): ObservationSiteDto {
        // 중복 관측지 존재 여부 확인 (이름 + 좌표 기준)
        val existing = observationSiteRepository.findAll().find {
            it.name == dto.name &&
                    it.latitude == dto.latitude &&
                    it.longitude == dto.longitude
        }

        val site = existing ?: observationSiteRepository.save(
            ObservationSite(
                name = dto.name,
                latitude = dto.latitude,
                longitude = dto.longitude
            )
        )

        // 저장 연결
        if (!userSavedSiteRepository.existsByUserIdAndSiteId(userId, site.id)) {
            userSavedSiteRepository.save(
                UserSavedSite(userId = userId, siteId = site.id)
            )
        }

        return ObservationSiteDto(
            name = site.name,
            latitude = site.latitude,
            longitude = site.longitude
        )
    }

    // 즐겨찾기에 저장된 관측지 삭제
    fun deleteSavedSite(userId: String, siteId: Long) {
        if (!userSavedSiteRepository.existsByUserIdAndSiteId(userId, siteId)) {
            throw IllegalArgumentException("해당 관측지는 즐겨찾기 목록에 없습니다.")
        }
        userSavedSiteRepository.deleteByUserIdAndSiteId(userId, siteId)
    }
}

