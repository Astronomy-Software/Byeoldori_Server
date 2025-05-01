package com.project.byeoldori.observationsites.dto

import com.project.byeoldori.observationsites.entity.ObservationSite
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

// 관측지 CRUD
data class ObservationSiteDto(
    @Schema(description = "관측지 이름", example = "별마로 천문대")
    val name: String,

    @Schema(description = "경도", example = "128.4865953418")
    val latitude: Double,

    @Schema(description = "위도", example = "37.1978774787")
    val longitude: Double,
)

// 관측지 추천 응답용 DTO
data class ObservationSiteResponseDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val score: Double
)

// 관측지 추천 입력 DTO
data class RecommendationRequestDto(
    val userLat: Double,
    val userLon: Double,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val observationTime: LocalDateTime
)

// DTO ➔ Entity 변환
fun ObservationSiteDto.toEntity(): ObservationSite {
    return ObservationSite(
        name = this.name,
        latitude = this.latitude,
        longitude = this.longitude
    )
}

// Entity ➔ DTO 변환 (score 포함)
fun ObservationSite.toDto(score: Double): ObservationSiteResponseDto {
    return ObservationSiteResponseDto(
        name = this.name,
        latitude = this.latitude,
        longitude = this.longitude,
        score = score
    )
}

fun ObservationSite.toSimpleDto() = ObservationSiteDto(
    name = this.name,
    latitude = this.latitude,
    longitude = this.longitude
)