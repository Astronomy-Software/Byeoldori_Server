package com.project.byeoldori.observationsites.service

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.observationsites.dto.*
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import com.project.byeoldori.user.entity.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserSavedSiteService(
    private val savedSiteRepo: UserSavedSiteRepository,
    private val siteRepo: ObservationSiteRepository
) {

    // 공식 관측지는 (user_id, site_id) UNIQUE(V9)라, 같은 사용자의 토글이 동시에 두 번 들어오면 한쪽 INSERT 가
    // 제약 위반으로 실패한다. 실패한 INSERT 가 바깥 트랜잭션을 롤백 전용으로 만들지 않도록 이 메서드는
    // 트랜잭션 없이 돌고(저장소 호출마다 짧은 트랜잭션), 위반이면 먼저 저장된 행을 돌려준다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun toggleSite(user: User, request: SiteToggleRequest): SiteToggleResponse {
        // 저장된 관측지(siteId) 토글
        if (request.siteId != null) {
            val site = siteRepo.findById(request.siteId).orElseThrow {
                NotFoundException(ErrorCode.SITE_NOT_FOUND)
            }
            val savedSiteOpt = savedSiteRepo.findByUserAndSite(user, site)

            return if (savedSiteOpt.isPresent) {
                savedSiteRepo.delete(savedSiteOpt.get())
                SiteToggleResponse(isSaved = false, savedSiteId = null)
            } else {
                saveOfficialSite(user, site)
            }
        }
        // 임의 장소(좌표) 토글
        else if (request.latitude != null && request.longitude != null) {
            val savedSiteOpt = savedSiteRepo.findByUserAndCustomLatitudeAndCustomLongitude(user, request.latitude, request.longitude)

            return if (savedSiteOpt.isPresent) {
                savedSiteRepo.delete(savedSiteOpt.get())
                SiteToggleResponse(isSaved = false, savedSiteId = null)
            } else {
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
            throw InvalidInputException("관측지 ID 또는 좌표 정보가 필요합니다.")
        }
    }

    // 동시에 들어온 다른 요청이 먼저 저장했으면 그 행을 그대로 돌려준다(이미 저장된 상태 = 성공).
    // 찾지 못하면 중복이 아닌 다른 제약 위반이므로 그대로 던진다.
    private fun saveOfficialSite(user: User, site: ObservationSite): SiteToggleResponse =
        try {
            val saved = savedSiteRepo.save(UserSavedSite(user = user, site = site))
            SiteToggleResponse(isSaved = true, savedSiteId = saved.id)
        } catch (e: DataIntegrityViolationException) {
            val existing = savedSiteRepo.findByUserAndSite(user, site).orElseThrow { e }
            SiteToggleResponse(isSaved = true, savedSiteId = existing.id)
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
            NotFoundException(ErrorCode.SAVED_SITE_NOT_FOUND, "즐겨찾기 목록에 없는 항목이거나 권한이 없습니다.")
        }
        return savedSite.toDto()
    }

    // 즐겨찾기에 저장된 관측지 삭제
    fun deleteSavedSite(user: User, savedSiteId: Long) {
        val savedSite = savedSiteRepo.findByIdAndUser(savedSiteId, user).orElseThrow {
            NotFoundException(ErrorCode.SAVED_SITE_NOT_FOUND)
        }
        savedSiteRepo.delete(savedSite)
    }

    private fun UserSavedSite.toDto(): SavedSiteResponseDto {
        return if (this.site != null) {
            SavedSiteResponseDto(
                savedSiteId = this.id,
                siteId = this.site!!.id,
                name = this.site!!.name,
                latitude = this.site!!.latitude,
                longitude = this.site!!.longitude,
                isCustom = false
            )
        } else {
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