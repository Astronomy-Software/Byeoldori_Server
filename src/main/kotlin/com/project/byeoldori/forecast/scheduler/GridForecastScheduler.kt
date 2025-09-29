package com.project.byeoldori.forecast.scheduler

import com.project.byeoldori.forecast.config.RetryProperties
import com.project.byeoldori.forecast.service.*
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.LocalDateTime
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
        val current = ZonedDateTime.now(seoul)
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

        // 패턴 시간(2,5,8,11,14,17,20,23)
        val patternHours = setOf(2, 5, 8, 11, 14, 17, 20, 23)

        // 1) 분/초/나노초를 0으로 맞춤 (정시 기준)
        var dateTime = current
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        // 2) "가장 가까운" 패턴 시각으로 이동
        //    - 현재 시각의 시(hour)가 patternHours에 없다면, 1시간씩 더하며 맞춤
        while (dateTime.hour !in patternHours) {
            dateTime = dateTime.minusHours(1)
        }

        // 3) 포맷팅하여 반환
        return dateTime.format(formatter)
    }

    fun getTMEFTimesForUltra(): List<String> {
        val now = ZonedDateTime.now(seoul)
        val floored = now.withMinute(0).withSecond(0).withNano(0)
        val offsets = if (now.minute == 0) listOf(0L, 1L, 2L, 3L, 4L, 5L) else listOf(1L, 2L, 3L, 4L, 5L, 6L)
        return offsets.map { floored.plusHours(it).format(ymdhm) }
    }

    fun getTMEFTimesForShortForecast(): List<String> {
        // 현재 시각
        var now = LocalDateTime.now()
        val patternHours = setOf(2, 5, 8, 11, 14, 17, 20, 23)

        // 1) 분(MM)이 0이 아니라면 다음 정시로 맞춤
        if (now.minute != 0) {
            now = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        } else {
            // 이미 정각이라면, 초/나노초만 0으로 맞춤
            now = now.withSecond(0).withNano(0)
        }

        // 2) 가장 가까운 패턴 시각(2,5,8,11,14,17,20,23)을 찾을 때까지 1시간씩 증가
        while (now.hour !in patternHours) {
            now = now.minusHours(1)
        }

        // 3) 패턴에 따라 종료 시각 결정
        val set1 = setOf(2, 5, 8, 11, 14)   // +2일 자정
        val set2 = setOf(17, 20, 23)       // +3일 자정

        val endDateTime = if (now.hour in set1) {
            now.toLocalDate().plusDays(3).atStartOfDay()
        } else {
            now.toLocalDate().plusDays(4).atStartOfDay()
        }

        // ─────────────────────────────────────────────────────
        //        // 4) 6시간 후부터 리스트 생성 (초단기예보와 겹치지 않게)
        // ─────────────────────────────────────────────────────
        // now가 "가장 가까운 패턴 시각"이므로, 거기서 +6시간 한 시점부터 시작
        var temp = now.plusHours(6).withMinute(0).withSecond(0).withNano(0)

        // 결과 리스트
        val result = mutableListOf<LocalDateTime>()

        // endDateTime까지 1시간 간격으로 추가
        while (!temp.isAfter(endDateTime)) {
            result.add(temp)
            temp = temp.plusHours(1)
        }

        // 5) 포맷팅 (yyyyMMddHHmm)
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        return result.map { it.format(formatter) }
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