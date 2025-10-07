package com.project.byeoldori.forecast.scheduler

import com.project.byeoldori.forecast.config.RetryProperties
import com.project.byeoldori.forecast.service.*
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil.getTMEFTimesForShortForecast
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil.getTMFCTimeForShort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
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

    // 특정시각마다 업데이트가 많이 느릴때가있음. 시간을 잘 조정해봐야할것으로 보임.
    // 매 10분 + 3분 간격으로 실행
    @Scheduled(cron = "0 3/10 * * * *", zone = "Asia/Seoul")
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

    // 2시 15분부터 3시간 간격으로 실행 -> 단기예보
    @Scheduled(cron = "0 15 2/3 * * *", zone = "Asia/Seoul")
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