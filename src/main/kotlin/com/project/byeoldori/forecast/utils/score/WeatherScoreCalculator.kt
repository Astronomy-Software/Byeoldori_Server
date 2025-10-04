package com.project.byeoldori.forecast.utils.score

import com.project.byeoldori.forecast.dto.*
import org.springframework.stereotype.Component
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Component
class WeatherScoreCalculator (
    private val astroCalculator: AstroCalculator,
){
    private val tmefFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    fun isUltraNoData(u: UltraForecastResponseDTO): Boolean {
        val skyInvalid = (u.sky == null || u.sky !in 1..4)
        val rehInvalid = (u.reh == null || u.reh <= 0 || u.reh > 100) // 0%·100%↑ 비정상 → 결측 취급
        val windZero   = (u.wsd == null || u.wsd <= 0f)
        val restZero   =
            (u.t1h == null || u.t1h == 0) &&
                    (u.vec == null || u.vec == 0) &&
                    (u.pty == null || u.pty == 0) &&
                    (u.rn1 == null || u.rn1 == 0f)

        // sky가 유효하지 않고, 습도/바람도 비정상이며 나머지도 0이면 결측으로 판단
        return skyInvalid && rehInvalid && windZero && restZero
    }

    fun suitabilityForUltra(item: UltraForecastResponseDTO, lightScore: Double): SuitabilityScoreDTO {
        if (isUltraNoData(item)) {
            return SuitabilityScoreDTO(
                total = 0,
                components = SuitabilityScoreDTO.Components(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                weights = weightsUltra,
            )
        }
        val sky = skyScoreFromInt(item.sky)
        val precip = precipScoreUltra(item.pty, item.rn1)
        val wind = windScore(item.wsd)
        val humidity = humidityScore(item.reh)

        val w = weightsUltra
        return pack(
            sky, precip, wind, humidity, moon = 0.0, light = 0.0, // 초단기는 하늘/강수/바람/습도 위주
            weights = w
        )
    }

    fun suitabilityForShort(item: ShortForecastResponseDTO, lightScore: Double, latitude: Double, longitude: Double)
    : SuitabilityScoreDTO {
        val time = parseTime(item.tmef)
        val sky = skyScoreFromInt(item.sky)
        val precip = precipScoreShort(item.pty, item.pop, item.pcp)
        val wind = windScore(item.wsd)
        val humidity = humidityScore(item.reh)
        val moon = moonScore(time, latitude, longitude)
        val lp = lightScore

        val w = weightsShort
        return pack(sky, precip, wind, humidity, moon, lp, w)
    }

    fun suitabilityForMid(item: MidCombinedForecastDTO, lightScore: Double, latitude: Double, longitude: Double)
    : SuitabilityScoreDTO {
        val time = parseMidTime(item.tmEf)
        val sky = skyScoreFromWB(item.sky)
        val precip = precipScoreMid(item.pre, item.rnSt)
        val wind = 0.5 // 데이터 부재 → 중립
        val humidity = 0.5
        val moon = moonScore(time, latitude, longitude)
        val lp = lightScore

        val w = weightsMid
        return pack(sky, precip, wind, humidity, moon, lp, w)
    }

    // ─────────────────────────────
    // 가중치 (합계 = 1.0)
    // ─────────────────────────────

    private val weightsUltra = SuitabilityScoreDTO.Weights(
        sky = 0.65, precipitation = 0.20, wind = 0.10, humidity = 0.05, moon = 0.0,  lightPollution = 0.0
    )

    private val weightsShort = SuitabilityScoreDTO.Weights(
        sky = 0.55, precipitation = 0.25, wind = 0.08, humidity = 0.06, moon = 0.05, lightPollution = 0.01
    )

    private val weightsMid = SuitabilityScoreDTO.Weights(
        sky = 0.50, precipitation = 0.30, wind = 0.00, humidity = 0.00, moon = 0.15, lightPollution = 0.05
    )

    // ─────────────────────────────
    // 개별 요소 점수화 (0.0 ~ 1.0)
    // ─────────────────────────────

    private fun skyScoreFromInt(sky: Int?): Double = when (sky) {
        1 -> 1.00 // 맑음
        2 -> 0.75 // 구름 조금
        3 -> 0.35 // 구름 많음
        4 -> 0.05 // 흐림
        else -> 0.50
    }

    private fun skyScoreFromWB(wb: String?): Double = when (wb) {
        "WB01" -> skyScoreFromInt(1)
        "WB02" -> skyScoreFromInt(2)
        "WB03" -> skyScoreFromInt(3)
        "WB04" -> skyScoreFromInt(4)
        else   -> 0.50
    }

    // 초단기: RN1(강수량)과 PTY(형태)로 강수 페널티
    private fun precipScoreUltra(pty: Int?, rn1: Float?): Double {
        if (rn1 != null && rn1 > 0f) return 0.0
        return when (pty) {
            null, 0 -> 1.0
            else    -> 0.25
        }
    }

    // 단기: POP(확률)·PCP(강수량)·PTY 종합
    private fun precipScoreShort(pty: Int?, pop: Int?, pcp: Float?): Double {
        if (pcp != null && pcp > 0f) return 0.0
        if (pty == null || pty == 0) {
            val p = (pop ?: 0).coerceIn(0, 100) / 100.0
            // 낮은 확률은 약하게, 60% 이상은 강하게 깎기 (지수 스케일)
            return (1.0 - p.pow(1.15)).coerceIn(0.0, 1.0)
        }
        return 0.25
    }

    // 중기: rnSt(%)와 pre 코드
    private fun precipScoreMid(pre: String?, rnSt: Int?): Double {
        if (pre == null || pre == "WB00") { // 무강수
            val p = rnSt?.coerceIn(0, 100)?.toDouble()?.div(100.0) ?: 0.0
            return 1.0 - p * 0.7 // 예측 신뢰도 완화
        }
        val p = rnSt?.coerceIn(0, 100)?.toDouble()?.div(100.0) ?: 1.0
        return (1.0 - p) * 0.3 // 강수형태가 있으면 강한 페널티
    }

    // 1~3 m/s 이상적, 7 m/s 이상 급감
    private fun windScore(wsd: Float?): Double {
        val v = wsd?.toDouble() ?: return 0.5
        return when {
            v < 1.0 -> 0.95
            v < 2.0 -> 1.00 // best
            v < 4.0 -> 0.85
            v < 6.0 -> 0.80
            v < 7.0 -> 0.60
            v < 9.0 -> 0.50
            else   -> 0.25
        }
    }

    private fun humidityScore(reh: Int?): Double {
        val h = reh?.toDouble() ?: return 0.5
        return when {
            h < 55 -> 1.00
            h < 65 -> 0.90
            h < 75 -> 0.70
            h < 85 -> 0.45
            h < 92 -> 0.30
            else   -> 0.18
        }
    }

    /** 달 위상 점수(보름달 페널티) */
    private fun moonScore(time: LocalDateTime, latitude: Double, longitude: Double): Double {
        val moonAltitude = astroCalculator.getMoonAltitude(time, latitude, longitude)

        if (moonAltitude < 0) {
            return 1.0 // 달이 졌으면 최고점
        }

        val illum = astroCalculator.getMoonIlluminatedFraction(time)
        val altitudeFactor = if (moonAltitude < 30) 0.7 else 1.0
        return (1.0 - 0.8 * illum * altitudeFactor).coerceIn(0.2, 1.0)
    }

    // ─────────────────────────────
    // 유틸 & 합성
    // ─────────────────────────────

    private fun parseTime(tmef: String): LocalDateTime =
        LocalDateTime.parse(tmef, tmefFmt)

    private fun parseMidTime(tmEf: String): LocalDateTime =
        if (tmEf.length == 12) LocalDateTime.parse(tmEf, tmefFmt)
        else LocalDateTime.parse("${tmEf.take(8)}1200", tmefFmt) // 일자만 오면 정오 가정

    private fun absMinutes(a: LocalDateTime, b: LocalDateTime): Long =
        abs(Duration.between(a, b).toMinutes())

    private fun shape(raw: Double): Double = raw.pow(0.97) // 0.8↑ 구간 살짝 상승

    private fun pack(
        sky: Double,
        precip: Double,
        wind: Double,
        humidity: Double,
        moon: Double,
        light: Double,
        weights: SuitabilityScoreDTO.Weights
    ): SuitabilityScoreDTO {
        val raw =
            sky * weights.sky +
                    precip * weights.precipitation +
                    wind * weights.wind +
                    humidity * weights.humidity +
                    moon * weights.moon +
                    light * weights.lightPollution

        val percent = (shape(raw.coerceIn(0.0, 1.0)) * 100.0).roundToInt()
        return SuitabilityScoreDTO(
            total = percent,
            components = SuitabilityScoreDTO.Components(
                sky = sky, precipitation = precip, wind = wind,
                humidity = humidity, moon = moon, lightPollution = light
            ),
            weights = weights,
        )
    }
}