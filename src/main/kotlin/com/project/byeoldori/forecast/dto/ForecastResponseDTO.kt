package com.project.byeoldori.forecast.dto

data class ForecastResponseDTO(
    val ultraForecastResponse : List<UltraForecastResponseDTO>,
    val shortForecastResponse : List<ShortForecastResponseDTO>,
    val midCombinedForecastDTO : List<MidCombinedForecastDTO>
)

data class UltraForecastResponseDTO(
    val tmef: String,
    val t1h: Int?,
    val vec: Int?,
    val wsd: Float?,
    val pty: Int?,
    val rn1: Float?,
    val reh: Int?,
    val sky: Int?,
    val suitability: Int = 0
)

data class ShortForecastResponseDTO(
    val tmef: String,
    val tmp: Int?,
    val tmx: Int?,
    val tmn: Int?,
    val vec: Float?,
    val wsd: Float?,
    val sky: Int?,
    val pty: Int?,
    val pop: Int?,
    val pcp: Float?,
    val sno: Float?,
    val reh: Int?,
    val suitability: Int = 0
)

data class MidForecastResponseDTO(
    val regId: String,
    val tmFc: String,
    val tmEf: String,
    val sky: String,
    val pre: String,
    val rnSt: Int
)

data class MidTempForecastResponseDTO(
    val regId: String,
    val tmFc: String,
    val tmEf: String,
    val min: Int,
    val max: Int,
)

data class MidCombinedForecastDTO(
    val tmFc: String,
    val tmEf: String,
    val doRegId: String,
    val siRegId: String,
    val sky: String?, // 하늘 상태 코드 WB01(맑음), WB02(구름조금), WB03(구름많음), WB04(흐림)
    val pre: String?, // 강수 유무 코드 WB09(비),WB11(비/눈),WB13(눈/비),WB12(눈)
    val rnSt: Int?,
    val min: Int?,
    val max: Int?,
    val suitability: Int = 0
)

data class SuitabilityScoreDTO(
    val total: Int, // 0~100
    val components: Components, // 원점수
    val weights: Weights, // 가중치
) {
    data class Components(
        val sky: Double,
        val precipitation: Double,
        val wind: Double,
        val humidity: Double,
        val moon: Double,
        val lightPollution: Double
    )
    data class Weights(
        val sky: Double,
        val precipitation: Double,
        val wind: Double,
        val humidity: Double,
        val moon: Double,
        val lightPollution: Double
    )
}