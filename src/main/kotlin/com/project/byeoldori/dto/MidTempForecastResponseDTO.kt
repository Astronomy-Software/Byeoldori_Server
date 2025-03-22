package com.project.byeoldori.dto

data class MidTempForecastResponseDTO(
    val regId: String,
    val tmFc: String,
    val tmEf: String,
    val modCode: String,
    val stn: String,
    val c: String,
    val min: String,
    val max: String,
    val minL: String,
    val minH: String,
    val maxL: String,
    val maxH: String
)
