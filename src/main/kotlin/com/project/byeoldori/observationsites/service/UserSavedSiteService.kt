package com.project.byeoldori.observationsites.service

import com.project.byeoldori.observationsites.dto.*
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import com.project.byeoldori.user.entity.User
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserSavedSiteService(
    private val savedSiteRepo: UserSavedSiteRepository,
    private val siteRepo: ObservationSiteRepository
) {

    fun toggleSite(user: User, request: SiteToggleRequest): SiteToggleResponse {
        // 저장된 관측지(siteId) 토글
        if (request.siteId != null) {
            val site = siteRepo.findById(request.siteId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 관측지입니다.")
            }
            val savedSiteOpt = savedSiteRepo.findByUserAndSite(user, site)

            return if (savedSiteOpt.isPresent) {
                // 이미 즐겨찾기 상태 -> 삭제
                savedSiteRepo.delete(savedSiteOpt.get())
                SiteToggleResponse(isSaved = false, savedSiteId = null)
            } else {
                // 즐겨찾기 아닌 상태 -> 추가
                val newSavedSite = savedSiteRepo.save(UserSavedSite(user = user, site = site))
                SiteToggleResponse(isSaved = true, savedSiteId = newSavedSite.id)
            }
        }
        // 임의 장소(좌표) 토글
        else if (request.latitude != null && request.longitude != null) {
            val savedSiteOpt = savedSiteRepo.findByUserAndCustomLatitudeAndCustomLongitude(user, request.latitude, request.longitude)

            return if (savedSiteOpt.isPresent) {
                // 이미 즐겨찾기 상태 -> 삭제
                savedSiteRepo.delete(savedSiteOpt.get())
                SiteToggleResponse(isSaved = false, savedSiteId = null)
            } else {
                // 즐겨찾기 아닌 상태 -> 추가
                val newCustomSite = UserSavedSite(
                    user = user,
                    customName = request.name ?: "이름 없는 장소",
                    customLatitude = request.latitude,
                    customLongitude = request.longitude
                )
                val newSavedSite = savedSiteRepo.save(newCustomSite)
                SiteToggleResponse(isSaved = true, savedSiteId = newSavedSite.id)
            }
        }
        // 요청 정보가 잘못된 경우
        else {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "관측지 ID 또는 좌표 정보가 필요합니다.")
        }
    }

    // 저장된 관측지 정보를 반환
    @Transactional(readOnly = true)
    fun getSavedSites(user: User): List<SavedSiteResponseDto> {
        return savedSiteRepo.findAllByUser(user).map { it.toDto() }
    }

    // 즐겨찾기 장소 상세 조회
    @Transactional(readOnly = true)
    fun getSavedSiteDetail(user: User, savedSiteId: Long): SavedSiteResponseDto {
        val savedSite = savedSiteRepo.findByIdAndUser(savedSiteId, user).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "즐겨찾기 목록에 없는 항목이거나 권한이 없습니다.")
        }
        return savedSite.toDto()
    }

    // 즐겨찾기에 저장된 관측지 삭제
    @Transactional
    fun deleteSavedSite(user: User, savedSiteId: Long) {
        val savedSite = savedSiteRepo.findByIdAndUser(savedSiteId, user).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "즐겨찾기 목록에 없는 항목입니다.")
        }
        savedSiteRepo.delete(savedSite)
    }

    private fun UserSavedSite.toDto(): SavedSiteResponseDto {
        return if (this.site != null) {
            // 공식 관측지인 경우
            SavedSiteResponseDto(
                savedSiteId = this.id,
                siteId = this.site!!.id,
                name = this.site!!.name,
                latitude = this.site!!.latitude,
                longitude = this.site!!.longitude,
                isCustom = false
            )
        } else {
            // 임의의 장소인 경우
            SavedSiteResponseDto(
                savedSiteId = this.id,
                siteId = null,
                name = this.customName ?: "이름 없음",
                latitude = this.customLatitude ?: 0.0,
                longitude = this.customLongitude ?: 0.0,
                isCustom = true
            )
        }
    }
}