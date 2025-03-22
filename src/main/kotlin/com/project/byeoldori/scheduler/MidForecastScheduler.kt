package com.project.byeoldori.scheduler

import com.project.byeoldori.api.WeatherData
import com.project.byeoldori.parser.MidForecastParser
import com.project.byeoldori.service.MidForecastService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class MidForecastScheduler(
    private val weatherData: WeatherData,
    private val midForecastParser: MidForecastParser,
    private val midForecastService: MidForecastService
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // 매일 06시 정각 실행
    @Scheduled(cron = "0 0 6 * * *")
    fun fetchAt06AM() {
        fetchAndSaveMidForecast("06시 스케줄")
    }

    // 매일 18시 정각 실행
    @Scheduled(cron = "0 0 18 * * *")
    fun fetchAt06PM() {
        fetchAndSaveMidForecast("18시 스케줄")
    }

    // 매시간 정각 삭제
    @Scheduled(cron = "0 0 * * * *")
    fun deleteOldData() {
        logger.info("24시간 지난 데이터 삭제 작업 시작")
        midForecastService.deleteOldForecasts()
    }

    private fun fetchAndSaveMidForecast(tag: String) {
        logger.info("[$tag] 중기 예보 호출 및 저장 시작")

        weatherData.fetchMidLandForecast()
            .flatMap { response ->
                val midForecastList = midForecastParser.parse(response)
                val savedForecasts = midForecastService.saveAll(midForecastList)
                logger.info("[$tag] ${savedForecasts.size}건 저장 완료!")
                Mono.empty<Void>()
            }
            .subscribe()
    }
}
