package com.project.byeoldori.observationsites.service

import com.project.byeoldori.forecast.service.ForeCastService
import com.project.byeoldori.observationsites.dto.ObservationSiteResponseDto
import com.project.byeoldori.observationsites.dto.toDto
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import org.springframework.stereotype.Service
import java.time.*
import kotlin.math.*

@Service
class ObservationSiteRecommendationService(
    // 이미 구현된 ForecastService를 주입받아 날씨 점수 계산에 사용
    private val observationSiteRepository: ObservationSiteRepository,
    private val forecastService: ForeCastService
) {
    // 예보 종류 구분
    enum class ForecastType { ULTRA, SHORT, MID }

    // 관측 시각에 따라 가장 적절한 예보 데이터를 사용하여 관측지 추천 리스트 반환
    fun recommendSites(userLat: Double, userLon: Double, observationTime: LocalDateTime): List<ObservationSiteResponseDto> {
        val sites = observationSiteRepository.findAll()

        return sites.map { site ->
            val type = getForecastType(observationTime)

            // 예보 기반 점수 추출
            val sky = forecastService.getSkyScoreByAllTypes(site.latitude, site.longitude, observationTime)
            val pty = forecastService.getPreScoreByAllTypes(site.latitude, site.longitude, observationTime)
            val wind = forecastService.getWindScoreByAllTypes(site.latitude, site.longitude, observationTime)

            // 중기 예보일 경우 달/광공해 점수 제외
            val moon = if (type == ForecastType.MID) null else getMoonPhaseScore(observationTime)
            val light = if (type == ForecastType.MID) null else getLightPollutionScore(site.latitude, site.longitude)

            // 점수 계산
            val score = calculateTotalScore(type, sky, pty, wind, moon, light)
            Pair(site, score)
        }
            .sortedByDescending { it.second } // 높은 점수순 정렬
            .take(5) // 상위 5개만 반환
            .map { (site, score) -> site.toDto(score)}
    }

    // 관측 시간으로부터 예보 타입 결정
    fun getForecastType(observationTime: LocalDateTime): ForecastType {
        val now = LocalDateTime.now()
        val hours = Duration.between(now, observationTime).toHours()
        return when {
            hours <= 6 -> ForecastType.ULTRA
            hours <= 72 -> ForecastType.SHORT
            else -> ForecastType.MID
        }
    }

    // 달의 위상 점수 계산
    fun getMoonPhaseScore(observationTime: LocalDateTime): Double {
        val knownNewMoon = LocalDateTime.of(2000, 1, 6, 18, 14)
        val moonCycle = 29.53058867

        // 관측 시각과 기준 시각 간의 차이를 밀리초 단위로 계산한 후 일수로 환산
        val diffInMillis = observationTime.toInstant(ZoneOffset.UTC).toEpochMilli() -
                knownNewMoon.toInstant(ZoneOffset.UTC).toEpochMilli()
        val diffInDays = diffInMillis.toDouble() / (1000 * 60 * 60 * 24)

        // 달의 주기로 나눈 나머지를 구하여 현재 위상(phase)을 계산 (음수 보정 포함)
        val phase = ((diffInDays % moonCycle) + moonCycle) % moonCycle
        // 0~1 사이로 정규화 (0 또는 1: 초승당, 그믐달, 0.5: 보름달)
        val normalized = phase / moonCycle

        // sine 함수를 사용하여 관측 적합도 점수를 매핑:
        // normalized = 0 또는 1일 때 (초승달, 그믐달) sin(0)=0, sin(π)=0 → score=1,
        // normalized = 0.5 (보름달)일 때 sin(π/2)=1 → score=0
        return 1 - sin(Math.PI * normalized)
    }

    // 광공해 값 → 점수로 변환 (어두울수록 높음)
    fun getLightPollutionScore(lat: Double, lon: Double): Double {
        val value = queryLightPollutionValue(lat, lon)
        return when { // 점수 조정 필요
            value < 1.0 -> 1.0
            value < 3.0 -> 0.8
            value < 6.0 -> 0.6
            value < 12.0 -> 0.4
            value < 20.0 -> 0.2
            else -> 0.0
        }
    }

    // TODO: 실제 광공해 데이터 연동 필요
    fun queryLightPollutionValue(lat: Double, lon: Double): Double {
        return 6.0 // 임시 값
    }

    // 예보 타입별로 점수 항목 및 가중치 다르게 계산
    fun calculateTotalScore(
        type: ForecastType,
        sky: Double,
        pty: Double,
        wind: Double?,
        moon: Double?,
        lightPollution: Double?
    ): Double {
        return when (type) {
            ForecastType.MID -> (0.5714 * sky) + (0.4286 * pty) // 40:30 비중 정규화
            else -> {
                val w = wind ?: 0.5
                val m = moon ?: 0.5
                val l = lightPollution ?: 0.5
                (0.40 * sky) + (0.30 * pty) + (0.05 * w) + (0.15 * m) + (0.10 * l)
            }
        }
    }
}

