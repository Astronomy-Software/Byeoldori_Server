package com.project.byeoldori.dto

data class MidCombinedForecastDTO(
    val tmFc: String,
    val tmEf: String,

    val doRegId: String,
    val siRegId: String,

    val sky: String?,
    val pre: String?,
    val wf: String?,
    val rnSt: Int?,

    val min: Int?,
    val max: Int?
)
