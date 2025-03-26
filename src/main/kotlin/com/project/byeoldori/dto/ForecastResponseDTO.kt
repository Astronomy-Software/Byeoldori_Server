package com.project.byeoldori.dto

data class ForecastResponseDTO(
    val ultraForecastResponse : List<UltraForecastResponseDTO>,
    val shortForecastResponse : List<ShortForecastResponseDTO>,
    val midForecastResponseDTO : List<MidForecastResponseDTO>,
)

// TODO : 이후 각각의 자료형 맞춰주기 Double이라 너무큰듯함.

data class UltraForecastResponseDTO(
    val tmef: String,
    val t1h: Double?,
    val vec: Double?,
    val wsd: Double?,
    val pty: Double?,
    val rn1: Double?,
    val reh: Double?
)

data class ShortForecastResponseDTO(
    val tmef: String,
    val tmp: Double?,
    val tmx: Double?,
    val tmn: Double?,
    val vec: Double?,
    val wsd: Double?,
    val sky: Double?,
    val pty: Double?,
    val pop: Double?,
    val rn1: Double?,
    val sno: Double?,
    val reh: Double?
)