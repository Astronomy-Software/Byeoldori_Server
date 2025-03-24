package com.project.byeoldori.scheduler

import com.project.byeoldori.api.WeatherData
import com.project.byeoldori.config.RetryProperties
import com.project.byeoldori.parser.MidForecastParser
import com.project.byeoldori.service.MidForecastService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*
import kotlin.concurrent.schedule

@Component
class MidForecastScheduler(
    private val weatherData: WeatherData,
    private val midForecastParser: MidForecastParser,
    private val midForecastService: MidForecastService,
    private val retryProperties: RetryProperties
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val maxRetryAttempts = retryProperties.attempts
    private val retryDelayMillis = retryProperties.delay * 1000L

    // 매일 06시 정각 실행
    @Scheduled(cron = "0 0 6 * * *")
    fun fetchAt06AM() {
        fetchAndSaveMidForecastWithRetry("06시 스케줄")
    }

    // 매일 18시 정각 실행
    @Scheduled(cron = "0 0 18 * * *")
    fun fetchAt06PM() {
        fetchAndSaveMidForecastWithRetry("18시 스케줄")
    }

    // 매시간 정각 삭제
    @Scheduled(cron = "0 0 * * * *")
    fun deleteOldData() {
        logger.info("24시간 지난 데이터 삭제 작업 시작")
        midForecastService.deleteOldForecasts()
    }

    private fun fetchAndSaveMidForecastWithRetry(tag: String, attempt: Int = 1) {
        logger.info("[$tag][$attempt/$maxRetryAttempts] 중기 예보 호출 및 저장 시도 시작")

        weatherData.fetchMidLandForecast()
            .flatMap { response ->
                val midForecastList = midForecastParser.parse(response)

                if (midForecastList.isEmpty()) {
                    logger.warn("[$tag][$attempt] 수신된 데이터가 비어있음 → 재시도 준비")
                    retryFetch(tag, attempt)
                    Mono.empty<Void>()
                } else {
                    val savedForecasts = midForecastService.saveAll(midForecastList)
                    logger.info("[$tag][$attempt] ${savedForecasts.size}건 저장 완료")
                    Mono.empty<Void>()
                }
            }
            .subscribe()
    }

    private fun retryFetch(tag: String, currentAttempt: Int) {
        if (currentAttempt < maxRetryAttempts) {
            logger.info("[$tag][$currentAttempt] ${retryDelayMillis / 60000}분 후 재시도 예정")
            Timer().schedule(retryDelayMillis) {
                fetchAndSaveMidForecastWithRetry(tag, currentAttempt + 1)
            }
        } else {
            logger.error("[$tag] 최대 재시도 횟수($maxRetryAttempts) 초과 → 중단")
        }
    }
}
