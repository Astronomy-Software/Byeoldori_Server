package com.project.byeoldori.scheduler

import com.project.byeoldori.api.WeatherData
import com.project.byeoldori.parser.MidTempForecastParser
import com.project.byeoldori.service.MidTempForecastService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class MidTempForecastScheduler(
    private val weatherData: WeatherData,
    private val midTempForecastParser: MidTempForecastParser,
    private val midTempForecastService: MidTempForecastService
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(cron = "0 0 6 * * *")
    fun fetchAt06AM() {
        fetchAndSaveMidTempForecast("06시 스케줄")
    }

    @Scheduled(cron = "0 0 18 * * *")
    fun fetchAt06PM() {
        fetchAndSaveMidTempForecast("18시 스케줄")
    }

    @Scheduled(cron = "0 0 * * * *")
    fun deleteOldData() {
        logger.info("[중기 기온 예보] 24시간 지난 데이터 삭제 시작")
        midTempForecastService.deleteOldForecasts()
    }

    private fun fetchAndSaveMidTempForecast(tag: String) {
        logger.info("[$tag][중기 기온 예보] 호출 및 저장 시작")

        weatherData.fetchMidTemperatureForecast()
            .flatMap { response ->
                val forecastList = midTempForecastParser.parse(response)
                val savedForecasts = midTempForecastService.saveAll(forecastList)
                logger.info("[$tag][중기 기온 예보] ${savedForecasts.size}건 저장 완료!")
                Mono.empty<Void>()
            }
            .subscribe()
    }
}
