package com.project.byeoldori.domain.midforecast

data class MidForecastResponseDTO(
    val regId: String,
    val tmFc: String,
    val tmEf: String,
    val modCode: String,
    val stn: String,
    val c: String,
    val sky: String,
    val pre: String,
    val conf: String,
    val wf: String,
    val rnSt: Int
)