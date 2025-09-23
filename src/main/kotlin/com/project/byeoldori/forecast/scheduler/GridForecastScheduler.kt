package com.project.byeoldori.forecast.scheduler

import com.project.byeoldori.forecast.config.RetryProperties
import com.project.byeoldori.forecast.service.*
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.schedule

@Component
class GridForecastScheduler(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val retryProperties: RetryProperties,
    private val midForecastService: MidForecastService,
    private val midTempForecastService: MidTempForecastService,
    private val midCombinedForecastService: MidCombinedForecastService

    ) {
    // 단 , 이파일에서 getTMFCTimeUltra 이런친구들은 외부로 보내도됨.
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val maxRetryAttempts = retryProperties.attempts
    private val retryDelayMillis = retryProperties.delay * 1000L

    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")
    private val ymdhm: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
    private val shortpattenhours = setOf(2, 5, 8, 11, 14, 17, 20, 23)

    // 특정시각마다 업데이트가 많이 느릴때가있음. 시간을 잘 조정해봐야할것으로 보임.
    // 매시각 10분마다 실행 -> 초단기예보 및 실황 , 일단 실황은 제외
    @Scheduled(cron = "0 0/10 * * * *", zone = "Asia/Seoul")
    fun clockForUltraForecast() {
        logger.info("초단기예보 및 실황정보 받아오는중...")

        fetchWithRetry(
            tag = "초단기예보",
            fetchFunction = {
                val tmfc = ForecastTimeUtil.getStableUltraTmfc()  // ← 60분 전 기준 안정적 tmfc
                val tmefList = ForecastTimeUtil.getNext6UltraTmef()
                ultraGridForecastService.updateAllUltraTMEFData(tmfc, tmefList)
                Mono.empty()
            }
        )
    }

    // 2시부터 3시간간격으로 실행 -> 단기예보
    @Scheduled(cron = "0 0 2/3 * * *", zone = "Asia/Seoul")
    fun clockForShortForecast() {
        logger.info("단기예보 받아오는중...")

        fetchWithRetry(
            tag = "단기예보",
            fetchFunction = {
                shortGridForecastService.updateAllShortTMEFData(getTMFCTimeForShort(), getTMEFTimesForShortForecast())
                Mono.empty()
            }
        )
    }

    // 06:10 / 18:10시 실행 중기 데이터 병합 및 저장
    @Scheduled(cron = "0 10 6,18 * * *", zone = "Asia/Seoul")
    fun collectMidCombinedDirect() {
        logger.info("중기 예보 수집·병합·저장 시작...")
        fetchWithRetry(
            tag = "중기예보",
            fetchFunction = {
                midCombinedForecastService.fetchAndSaveFromApi()
                Mono.empty()
            }
        )
    }

    // 06:30 / 18:30마다 24시간 지난 중기 육상 예보 삭제
    @Scheduled(cron = "0 30 6,18 * * *", zone = "Asia/Seoul")
    fun deleteOldMidForecasts() {
        logger.info("24시간 지난 중기 육상 데이터 삭제 시작")
        midForecastService.deleteOldForecasts()
    }

    // 매 정각마다 24시간 지난 중기 기온 예보 삭제
    @Scheduled(cron = "0 30 6,18 * * *", zone = "Asia/Seoul")
    fun deleteOldMidTempForecasts() {
        logger.info("24시간 지난 중기 기온 데이터 삭제 시작")
        midTempForecastService.deleteOldForecasts()
    }

    // 매 정각마다 24시간 지난 중기 통합 예보 삭제
    @Scheduled(cron = "0 35 6,18 * * *", zone = "Asia/Seoul")
    fun deleteOldMidCombinedForecasts() {
        logger.info("24시간 지난 중기 병합 예보 삭제 시작")
        midCombinedForecastService.deleteOldForecasts()
    }

    fun getTMFCTimeUltra(): String {
        val now = ZonedDateTime.now(seoul)
        val flooredMinutes = (now.minute / 10) * 10
        val base = now.withMinute(flooredMinutes).withSecond(0).withNano(0)
        return base.format(ymdhm)
    }

    fun getTMFCTimeForShort(): String {
        var base = ZonedDateTime.now(seoul).withSecond(0).withNano(0).withMinute(0)
        while (base.hour !in shortpattenhours) {
            base = base.minusHours(1)
        }
        return base.format(ymdhm)
    }

    fun getTMEFTimesForUltra(): List<String> {
        val now = ZonedDateTime.now(seoul)
        val floored = now.withMinute(0).withSecond(0).withNano(0)
        val offsets = if (now.minute == 0) listOf(0L, 1L, 2L, 3L, 4L, 5L) else listOf(1L, 2L, 3L, 4L, 5L, 6L)
        return offsets.map { floored.plusHours(it).format(ymdhm) }
    }

    fun getTMEFTimesForShortForecast(): List<String> {
        // 1) 기준 시각: 최근 패턴 정시(KST)
        var base = ZonedDateTime.now(seoul).withSecond(0).withNano(0).withMinute(0)
        while (base.hour !in shortpattenhours) {
            base = base.minusHours(1)
        }

        // 2) 초단기와 겹치지 않게 +6h부터 시작 (기존 로직 유지)
        var cursor = base.plusHours(6).withMinute(0).withSecond(0).withNano(0)

        // 3) 종료 시각 결정(기존 규칙 유지)
        val set1 = setOf(2, 5, 8, 11, 14)   // +2일 자정까지(== base + 3일 00:00)
        val set2 = setOf(17, 20, 23)        // +3일 자정까지(== base + 4일 00:00)
        val end = if (base.hour in set1) {
            base.toLocalDate().plusDays(3).atStartOfDay(seoul)
        } else {
            base.toLocalDate().plusDays(4).atStartOfDay(seoul)
        }

        // 4) 1시간 간격으로 end까지 생성
        val result = mutableListOf<String>()
        while (!cursor.isAfter(end)) {
            result.add(cursor.format(ymdhm))
            cursor = cursor.plusHours(1)
        }
        return result
    }

    // 공통 재시도 fetch 처리
    fun fetchWithRetry(tag: String, attempt: Int = 1, fetchFunction: () -> Mono<Void>) {
        logger.info("[$tag][$attempt/$maxRetryAttempts] 실행 시도 중...")
        try {
            fetchFunction()
                .doOnError {
                    logger.error("[$tag][$attempt] 오류 발생: ${it.message}", it)
                    retry(tag, attempt, fetchFunction)
                }
                .subscribe()
        } catch (e: Exception) {
            logger.error("[$tag][$attempt] 예외 발생", e)
            retry(tag, attempt, fetchFunction)
        }
    }

    fun retry(tag: String, currentAttempt: Int, fetchFunction: () -> Mono<Void>) {
        if (currentAttempt < maxRetryAttempts) {
            logger.info("[$tag][$currentAttempt] ${retryDelayMillis / 60000}분 후 재시도 예정")
            Timer().schedule(retryDelayMillis) {
                fetchWithRetry(tag, currentAttempt + 1, fetchFunction)
            }
        } else {
            logger.error("[$tag] 최대 재시도 횟수($maxRetryAttempts) 초과 → 중단")
        }
    }
}