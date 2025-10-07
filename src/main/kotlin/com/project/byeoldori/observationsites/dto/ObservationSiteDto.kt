package com.project.byeoldori.observationsites.dto

import com.project.byeoldori.observationsites.entity.ObservationSite
import io.swagger.v3.oas.annotations.media.Schema

// 관측지 CRUD
data class ObservationSiteDto(
    @Schema(description = "관측지 이름", example = "별마로 천문대")
    val name: String,

    @Schema(description = "위도", example = "37.1978774787")
    val latitude: Double,

    @Schema(description = "경도", example = "128.4865953418")
    val longitude: Double,
)

// 관측지 상세 정보 DTO
data class ObservationSiteDetailDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val reviewCount: Long,
    val totalLikes: Long,
    val averageScore: Double
)

@Schema(description = "즐겨찾기 토글 요청 DTO")
data class SiteToggleRequest(
    @Schema(description = "관측지 ID 저장된 관측지 토글 시 사용")
    val siteId: Long? = null,

    @Schema(description = "사용자 지정 장소 즐겨찾기(임의 장소 즐겨찾기 시)", example = "저장된 관측지가 아니고 임의의 장소 즐겨찾기시" +
            " name, lat, lon 입력")
    val name: String? = null,

    @Schema(description = "위도(임의 장소 즐겨찾기 시)")
    val latitude: Double? = null,

    @Schema(description = "경도 (임의 장소 즐겨찾기 시)")
    val longitude: Double? = null
)

@Schema(description = "즐겨찾기 토글 응답 DTO")
data class SiteToggleResponse(
    @Schema(description = "최종 즐겨찾기 상태 (true: 추가됨, false: 삭제됨)")
    val isSaved: Boolean,
    @Schema(description = "즐겨찾기 항목의 고유 ID (추가된 경우에만 반환됨)")
    val savedSiteId: Long?
)

data class SavedSiteResponseDto(
    val savedSiteId: Long, // 즐겨찾기 항목 자체의 ID
    val siteId: Long?,     // 공식 관측지의 ID
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isCustom: Boolean // 공식(false) / 개인(true)
)

// DTO ➔ Entity 변환
fun ObservationSiteDto.toEntity(): ObservationSite {
    return ObservationSite(
        name = this.name,
        latitude = this.latitude,
        longitude = this.longitude
    )
}