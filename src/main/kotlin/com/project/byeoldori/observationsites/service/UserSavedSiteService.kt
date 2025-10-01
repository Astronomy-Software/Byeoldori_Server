package com.project.byeoldori.observationsites.service

import com.project.byeoldori.observationsites.dto.ObservationSiteDto
import com.project.byeoldori.observationsites.dto.toSimpleDto
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

    // 추천된 관측지를 사용자 저장 목록에 추가
    @Transactional
    fun saveSite(user: User, siteId: Long) {
        val site = siteRepo.findById(siteId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 관측지입니다.")
        }
        if (savedSiteRepo.existsByUserAndSite(user, site)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 저장된 관측지입니다.")
        }
        savedSiteRepo.save(UserSavedSite(user = user, site = site))
    }

    // 저장된 관측지 정보를 반환
    @Transactional(readOnly = true)
    fun getSavedSites(user: User): List<ObservationSiteDto> {
        return savedSiteRepo.findAllByUser(user).map { it.site.toSimpleDto() }
    }

    // 즐겨찾기에 저장된 관측지 삭제
    fun deleteSite(user: User, siteId: Long) {
        val site = siteRepo.findById(siteId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 관측지입니다.")
        }
        val savedSite = savedSiteRepo.findByUserAndSite(user, site)
        if (savedSite != null) {
            savedSiteRepo.delete(savedSite)
        } else {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "저장되지 않은 관측지입니다.")
        }
    }
}