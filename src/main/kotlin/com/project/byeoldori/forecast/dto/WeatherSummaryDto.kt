package com.project.byeoldori.forecast.dto

data class WeatherSummaryDto(
    val suitability: Int,      // 현재 관측 적합도 0~100
    val sky: String,           // 현재 하늘 상태 텍스트 (맑음/구름많음/흐림 등)
    val temperature: Int?,     // 현재 기온 (°C)
    val nextGoodTime: String?  // 다음 관측 적합 시각 (yyyyMMddHHmm), 없으면 null
)
