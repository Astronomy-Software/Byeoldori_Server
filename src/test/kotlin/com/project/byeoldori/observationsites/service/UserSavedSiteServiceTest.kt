package com.project.byeoldori.observationsites.service

import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.observationsites.dto.SiteToggleRequest
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.Optional

/**
 * 즐겨찾기 토글 분기(공식 관측지 / 임의 좌표 / 잘못된 요청)와 응답 매핑.
 */
class UserSavedSiteServiceTest {

    private val savedRepo = mock(UserSavedSiteRepository::class.java)
    private val siteRepo = mock(ObservationSiteRepository::class.java)
    private val service = UserSavedSiteService(savedRepo, siteRepo)

    private val me = User(id = 1, email = "me@test.com", passwordHash = "x", name = "나", phone = "010")
    private val site = ObservationSite(id = 10, name = "별마로 천문대", latitude = 37.1978, longitude = 128.4865)

    private fun saveReturnsArgument() {
        `when`(savedRepo.save(any(UserSavedSite::class.java))).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `siteId 도 좌표도 없으면 InvalidInputException`() {
        assertThatThrownBy { service.toggleSite(me, SiteToggleRequest()) }
            .isInstanceOf(InvalidInputException::class.java)
        // 좌표가 한 쪽만 있어도 잘못된 요청
        assertThatThrownBy { service.toggleSite(me, SiteToggleRequest(latitude = 37.0)) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThatThrownBy { service.toggleSite(me, SiteToggleRequest(longitude = 127.0)) }
            .isInstanceOf(InvalidInputException::class.java)
        verify(savedRepo, never()).save(any(UserSavedSite::class.java))
    }

    @Test
    fun `없는 관측지 id 는 SITE_NOT_FOUND`() {
        `when`(siteRepo.findById(99L)).thenReturn(Optional.empty())
        assertThatThrownBy { service.toggleSite(me, SiteToggleRequest(siteId = 99)) }
            .isInstanceOf(NotFoundException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.SITE_NOT_FOUND)
    }

    @Test
    fun `공식 관측지 - 저장돼 있지 않으면 추가`() {
        `when`(siteRepo.findById(10L)).thenReturn(Optional.of(site))
        `when`(savedRepo.findByUserAndSite(me, site)).thenReturn(Optional.empty())
        `when`(savedRepo.save(any(UserSavedSite::class.java))).thenReturn(UserSavedSite(id = 77, user = me, site = site))

        val res = service.toggleSite(me, SiteToggleRequest(siteId = 10))

        assertThat(res.isSaved).isTrue()
        assertThat(res.savedSiteId).isEqualTo(77)
        verify(savedRepo, never()).delete(any(UserSavedSite::class.java))
    }

    @Test
    fun `공식 관측지 - 이미 저장돼 있으면 삭제`() {
        val existing = UserSavedSite(id = 5, user = me, site = site)
        `when`(siteRepo.findById(10L)).thenReturn(Optional.of(site))
        `when`(savedRepo.findByUserAndSite(me, site)).thenReturn(Optional.of(existing))

        val res = service.toggleSite(me, SiteToggleRequest(siteId = 10))

        assertThat(res.isSaved).isFalse()
        assertThat(res.savedSiteId).isNull()
        verify(savedRepo).delete(existing)
        verify(savedRepo, never()).save(any(UserSavedSite::class.java))
    }

    @Test
    fun `siteId 와 좌표가 함께 오면 siteId 가 우선`() {
        `when`(siteRepo.findById(10L)).thenReturn(Optional.of(site))
        `when`(savedRepo.findByUserAndSite(me, site)).thenReturn(Optional.empty())
        saveReturnsArgument()

        service.toggleSite(me, SiteToggleRequest(siteId = 10, name = "임의", latitude = 1.0, longitude = 2.0))

        verify(savedRepo, never()).findByUserAndCustomLatitudeAndCustomLongitude(me, 1.0, 2.0)
    }

    @Test
    fun `임의 좌표 - 처음이면 이름·좌표를 담아 추가, 이름이 없으면 기본 이름`() {
        `when`(savedRepo.findByUserAndCustomLatitudeAndCustomLongitude(me, 36.5, 127.5)).thenReturn(Optional.empty())
        saveReturnsArgument()

        val named = service.toggleSite(me, SiteToggleRequest(name = "뒷산", latitude = 36.5, longitude = 127.5))
        assertThat(named.isSaved).isTrue()

        val captor = org.mockito.ArgumentCaptor.forClass(UserSavedSite::class.java)
        verify(savedRepo).save(captor.capture())
        assertThat(captor.value.site).isNull()
        assertThat(captor.value.customName).isEqualTo("뒷산")
        assertThat(captor.value.customLatitude).isEqualTo(36.5)
        assertThat(captor.value.customLongitude).isEqualTo(127.5)

        service.toggleSite(me, SiteToggleRequest(latitude = 36.5, longitude = 127.5))
        verify(savedRepo, org.mockito.Mockito.times(2)).save(captor.capture())
        assertThat(captor.allValues.last().customName).isEqualTo("이름 없는 장소")
    }

    @Test
    fun `임의 좌표 - 같은 좌표가 이미 있으면 삭제`() {
        val existing = UserSavedSite(id = 8, user = me, customName = "뒷산", customLatitude = 36.5, customLongitude = 127.5)
        `when`(savedRepo.findByUserAndCustomLatitudeAndCustomLongitude(me, 36.5, 127.5)).thenReturn(Optional.of(existing))

        val res = service.toggleSite(me, SiteToggleRequest(latitude = 36.5, longitude = 127.5))

        assertThat(res.isSaved).isFalse()
        assertThat(res.savedSiteId).isNull()
        verify(savedRepo).delete(existing)
    }

    @Test
    fun `목록 응답 - 공식 관측지는 site 정보, 임의 장소는 custom 정보로 매핑`() {
        `when`(savedRepo.findAllByUser(me)).thenReturn(listOf(
            UserSavedSite(id = 1, user = me, site = site),
            UserSavedSite(id = 2, user = me, customName = "옥상", customLatitude = 37.0, customLongitude = 127.0),
            UserSavedSite(id = 3, user = me), // 이름·좌표가 모두 비어 있는 손상 행
        ))

        val list = service.getSavedSites(me)

        assertThat(list).hasSize(3)
        with(list[0]) {
            assertThat(savedSiteId).isEqualTo(1); assertThat(siteId).isEqualTo(10); assertThat(name).isEqualTo("별마로 천문대")
            assertThat(latitude).isEqualTo(37.1978); assertThat(isCustom).isFalse()
        }
        with(list[1]) {
            assertThat(savedSiteId).isEqualTo(2); assertThat(siteId).isNull(); assertThat(name).isEqualTo("옥상")
            assertThat(latitude).isEqualTo(37.0); assertThat(longitude).isEqualTo(127.0); assertThat(isCustom).isTrue()
        }
        with(list[2]) {
            assertThat(name).isEqualTo("이름 없음"); assertThat(latitude).isEqualTo(0.0); assertThat(longitude).isEqualTo(0.0); assertThat(isCustom).isTrue()
        }
    }

    @Test
    fun `상세·삭제 - 내 항목이 아니면 SAVED_SITE_NOT_FOUND`() {
        `when`(savedRepo.findByIdAndUser(5L, me)).thenReturn(Optional.empty())
        assertThatThrownBy { service.getSavedSiteDetail(me, 5) }
            .isInstanceOf(NotFoundException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.SAVED_SITE_NOT_FOUND)
        assertThatThrownBy { service.deleteSavedSite(me, 5) }
            .isInstanceOf(NotFoundException::class.java)
        verify(savedRepo, never()).delete(any(UserSavedSite::class.java))
    }

    @Test
    fun `삭제 - 내 항목이면 delete`() {
        val mine = UserSavedSite(id = 6, user = me, site = site)
        `when`(savedRepo.findByIdAndUser(6L, me)).thenReturn(Optional.of(mine))
        service.deleteSavedSite(me, 6)
        verify(savedRepo).delete(mine)
    }
}
