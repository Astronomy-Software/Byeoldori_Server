package com.project.byeoldori.forecast.utils.score

import org.apache.commons.math3.util.FastMath
import org.springframework.stereotype.Component
import java.time.*

@Component
class AstroCalculator {

    /** 달의 조도 비율 (0..1) — 간단·안정식 */
    fun getMoonIlluminatedFraction(dateTime: LocalDateTime, zone: ZoneId = ZoneId.of("Asia/Seoul")): Double {
        val utc = dateTime.atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
        val synodic = 29.53058867 // 평균 삭망월 (일)
        val knownNewMoon = ZonedDateTime.of(2000, 1, 6, 18, 14, 0, 0, ZoneOffset.UTC)
        val days = Duration.between(knownNewMoon, utc).toMinutes().toDouble() / 1440.0
        val phase = twoPi * frac(days / synodic) // 0..2π
        // 신월(0) -> 0, 보름(π) -> 1
        return 0.5 * (1.0 - FastMath.cos(phase))
    }

    /** 달의 고도(도) — 간단한 근사식(수° 정확도): zone을 UTC로 변환하여 계산 */
    fun getMoonAltitude(dateTime: LocalDateTime, latitude: Double, longitude: Double, zone: ZoneId = ZoneId.of("Asia/Seoul")): Double {
        val utc = dateTime.atZone(zone).withZoneSameInstant(ZoneOffset.UTC)
        val jd = toJulianDateUTC(utc)
        val d = jd - J2000

        // 궤도 요소(근사): 간이 모델
        val N = deg2rad(normDeg(125.1228 - 0.0529538083 * d)) // 승교점 경도
        val i = deg2rad(5.1454)                                // 궤도 경사
        val w = deg2rad(normDeg(318.0634 + 0.1643573223 * d)) // 근일점 인수
        val a = 60.2666                                       // 평균 거리(지구반지름단위)
        val e = 0.054900                                      // 이심률
        val M = deg2rad(normDeg(115.3654 + 13.0649929509 * d))// 평균 근점 이각(달)

        // 케플러 방정식: E - e sinE = M (뉴턴-랩슨, e 포함!)
        var E = M
        repeat(10) {
            val f = E - e * FastMath.sin(E) - M
            val f1 = 1.0 - e * FastMath.cos(E)
            E -= f / f1
        }

        // 궤도면 좌표
        val xv = a * (FastMath.cos(E) - e)
        val yv = a * (FastMath.sqrt(1 - e * e) * FastMath.sin(E))
        val v = FastMath.atan2(yv, xv)               // 진근점이각
        val r = FastMath.hypot(xv, yv)               // 거리

        // 황도좌표 (N, i, w 적용)
        val xh = r * (FastMath.cos(N) * FastMath.cos(v + w) - FastMath.sin(N) * FastMath.sin(v + w) * FastMath.cos(i))
        val yh = r * (FastMath.sin(N) * FastMath.cos(v + w) + FastMath.cos(N) * FastMath.sin(v + w) * FastMath.cos(i))
        val zh = r * (FastMath.sin(v + w) * FastMath.sin(i))

        val lonEcl = FastMath.atan2(yh, xh)          // ecliptic longitude
        val latEcl = FastMath.atan2(zh, FastMath.hypot(xh, yh)) // ecliptic latitude

        // 적도좌표로 변환 (황도경사)
        val eps = deg2rad(23.4397 - 0.0000004 * d)
        val xe = FastMath.cos(lonEcl) * FastMath.cos(latEcl)
        val ye = FastMath.sin(lonEcl) * FastMath.cos(latEcl) * FastMath.cos(eps) - FastMath.sin(latEcl) * FastMath.sin(eps)
        val ze = FastMath.sin(lonEcl) * FastMath.cos(latEcl) * FastMath.sin(eps) + FastMath.sin(latEcl) * FastMath.cos(eps)

        val ra = FastMath.atan2(ye, xe)              // 0..2π
        val dec = FastMath.atan2(ze, FastMath.hypot(xe, ye))

        // 국지 항성시 (JD에서 바로 GMST 구한 뒤 경도 보정)
        val lst = localSiderealTimeRad(jd, longitude)

        // 시각차(HA) → 고도
        val ha = normRad(lst - ra)
        val lat = deg2rad(latitude)
        val sinAlt = FastMath.sin(dec) * FastMath.sin(lat) + FastMath.cos(dec) * FastMath.cos(lat) * FastMath.cos(ha)
        return rad2deg(FastMath.asin(sinAlt))
    }

    // =============================
    // Helpers
    // =============================

    private fun toJulianDateUTC(zdt: ZonedDateTime): Double {
        // JD = (Unix epoch millis / 하루ms) + 2440587.5
        val jd = zdt.toInstant().toEpochMilli() / 86_400_000.0 + 2440587.5
        return jd
    }

    private fun localSiderealTimeRad(jd: Double, longitudeDeg: Double): Double {
        val T = (jd - J2000) / 36525.0
        // GMST in degrees (IAU 1982/2000 근사)
        var theta = 280.46061837 + 360.98564736629 * (jd - J2000) +
                0.000387933 * T * T - (T * T * T) / 38710000.0
        theta = normDeg(theta + longitudeDeg) // 경도(동경+) 가산 → LST(deg)
        return deg2rad(theta)
    }

    private fun frac(x: Double) = x - kotlin.math.floor(x)
    private fun normDeg(x: Double): Double {
        var v = x % 360.0
        if (v < 0) v += 360.0
        return v
    }
    private fun normRad(a: Double): Double {
        var v = a % twoPi
        if (v < 0) v += twoPi
        return v
    }

    private fun deg2rad(d: Double) = d * d2r
    private fun rad2deg(r: Double) = r * r2d

    companion object {
        private const val J2000 = 2451545.0
        private const val d2r = Math.PI / 180.0
        private const val r2d = 180.0 / Math.PI
        private const val twoPi = 2.0 * Math.PI
    }
}