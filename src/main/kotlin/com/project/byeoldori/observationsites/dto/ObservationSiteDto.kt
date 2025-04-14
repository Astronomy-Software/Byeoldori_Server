package com.project.byeoldori.observationsites.dto

import com.project.byeoldori.observationsites.entity.ObservationSite
import io.swagger.v3.oas.annotations.media.Schema

data class ObservationSiteDto(
    @Schema(description = "관측지 이름", example = "별마로 천문대")
    val name: String,

    @Schema(description = "경도", example = "128.4865953418")
    val latitude: Double,

    @Schema(description = "위도", example = "37.1978774787")
    val longitude: Double
)

fun ObservationSiteDto.toEntity(): ObservationSite {
    return ObservationSite(
        name = this.name,
        latitude = this.latitude,
        longitude = this.longitude
    )
}


