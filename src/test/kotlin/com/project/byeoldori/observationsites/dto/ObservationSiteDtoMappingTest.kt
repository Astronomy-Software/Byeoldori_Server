package com.project.byeoldori.observationsites.dto

import com.project.byeoldori.observationsites.entity.ObservationSite
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObservationSiteDtoMappingTest {

    @Test
    fun `요청 DTO → 엔티티 - id 는 0 (자동 생성 대기)`() {
        val entity = ObservationSiteDto(name = "별마로 천문대", latitude = 37.1978774787, longitude = 128.4865953418).toEntity()
        assertThat(entity.id).isEqualTo(0)
        assertThat(entity.name).isEqualTo("별마로 천문대")
        assertThat(entity.latitude).isEqualTo(37.1978774787)
        assertThat(entity.longitude).isEqualTo(128.4865953418)
    }

    @Test
    fun `엔티티 → 응답 DTO - 평점은 생성·수정 응답에서 null`() {
        val res = ObservationSite(id = 5, name = "소백산", latitude = 36.95, longitude = 128.45).toResponseDto()
        assertThat(res).isEqualTo(ObservationSiteResponseDto(id = 5, name = "소백산", latitude = 36.95, longitude = 128.45, averageScore = null))
    }

    @Test
    fun `왕복 변환은 이름·좌표를 보존한다`() {
        val dto = ObservationSiteDto(name = "x", latitude = -0.0, longitude = 180.0)
        val back = dto.toEntity().toResponseDto()
        assertThat(back.name).isEqualTo(dto.name)
        assertThat(back.latitude).isEqualTo(dto.latitude)
        assertThat(back.longitude).isEqualTo(dto.longitude)
    }

    @Test
    fun `토글 요청 기본값은 모두 null`() {
        val req = SiteToggleRequest()
        assertThat(req.siteId).isNull()
        assertThat(req.name).isNull()
        assertThat(req.latitude).isNull()
        assertThat(req.longitude).isNull()
    }
}
