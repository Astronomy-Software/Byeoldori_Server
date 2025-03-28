package com.project.byeoldori.dto

data class ForecastResponseDTO(
    val ultraForecastResponse : List<UltraForecastResponseDTO>,
    val shortForecastResponse : List<ShortForecastResponseDTO>,
    val midForecastResponseDTO : List<MidForecastResponseDTO>,
    val midTempForecastResponseDTO : List<MidTempForecastResponseDTO>,
    val midCombinedForecastDTO : List<MidCombinedForecastDTO>
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

data class MidTempForecastResponseDTO(
    val regId: String,
    val tmFc: String,
    val tmEf: String,
    val modCode: String,
    val stn: String,
    val c: String,
    val min: Int,
    val max: Int,
    val minL: Int,
    val minH: Int,
    val maxL: Int,
    val maxH: Int
)

data class MidCombinedForecastDTO(
    val tmFc: String,
    val tmEf: String,

    val doRegId: String,
    val siRegId: String,

    val sky: String?, // 하늘 상태 코드 WB01(맑음), WB02(구름조금), WB03(구름많음), WB04(흐림)
    val pre: String?, // 강수 유무 코드 WB09(비),WB11(비/눈),WB13(눈/비),WB12(눈)
    val wf: String?,
    val rnSt: Int?,

    val min: Int?,
    val max: Int?
)
    // TODO : DTO 병합 - mid 내용들 여기로 추가