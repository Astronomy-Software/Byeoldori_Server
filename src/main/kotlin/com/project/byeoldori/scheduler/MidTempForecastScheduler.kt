package com.project.byeoldori.scheduler

import com.project.byeoldori.api.WeatherData
import com.project.byeoldori.parser.MidTempForecastParser
import com.project.byeoldori.service.MidTempForecastService
import com.project.byeoldori.config.RetryProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.*
import kotlin.concurrent.schedule


@Component
class MidTempForecastScheduler(
    private val weatherData: WeatherData,
    private val midTempForecastParser: MidTempForecastParser,
    private val midTempForecastService: MidTempForecastService,
    private val retryProperties: RetryProperties
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val maxRetryAttempts = retryProperties.attempts
    private val retryDelayMillis = retryProperties.delay * 1000L

    @Scheduled(cron = "0 0 6 * * *")
    fun fetchAt06AM() {
        fetchAndSaveMidTempForecastWithRetry("06시 스케줄")
    }

    @Scheduled(cron = "0 0 18 * * *")
    fun fetchAt06PM() {
        fetchAndSaveMidTempForecastWithRetry("18시 스케줄")
    }

    @Scheduled(cron = "0 0 * * * *")
    fun deleteOldData() {
        logger.info("[중기 기온 예보] 24시간 지난 데이터 삭제 시작")
        midTempForecastService.deleteOldForecasts()
    }

    private fun fetchAndSaveMidTempForecastWithRetry(tag: String, attempt: Int = 1) {
        logger.info("[$tag][$attempt/$maxRetryAttempts][중기 기온 예보] 호출 및 저장 시도 시작")

        weatherData.fetchMidTemperatureForecast()
            .flatMap { response ->
                val forecastList = midTempForecastParser.parse(response)

                if (forecastList.isEmpty()) {
                    logger.warn("[$tag][$attempt] 수신된 기온 예보 데이터가 비어있음 → 재시도 예정")
                    retryFetch(tag, attempt)
                    Mono.empty<Void>()
                } else {
                    val savedForecasts = midTempForecastService.saveAll(forecastList)
                    logger.info("[$tag][$attempt] 중기 기온 예보 ${savedForecasts.size}건 저장 완료!")
                    Mono.empty<Void>()
                }
            }
            .subscribe()
    }

    private fun retryFetch(tag: String, currentAttempt: Int) {
        if (currentAttempt < maxRetryAttempts) {
            logger.info("[$tag][$currentAttempt] ${retryDelayMillis / 60000}분 후 재시도 예정")
            Timer().schedule(retryDelayMillis) {
                fetchAndSaveMidTempForecastWithRetry(tag, currentAttempt + 1)
            }
        } else {
            logger.error("[$tag] 최대 재시도 횟수($maxRetryAttempts) 초과 → 재시도 중단")
        }
    }
}
