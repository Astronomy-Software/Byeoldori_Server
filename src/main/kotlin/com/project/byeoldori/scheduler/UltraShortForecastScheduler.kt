package com.project.byeoldori.scheduler

import com.project.byeoldori.api.WeatherData
import com.project.byeoldori.config.RetryProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.schedule

@Component
class UltraShortForecastScheduler(
    private val weatherData: WeatherData,
    private val retryProperties: RetryProperties
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    private val retryDelayMillis = retryProperties.delay * 1000L
    private val maxRetryAttempts = retryProperties.attempts

    @Scheduled(cron = "0 */10 * * * *")
    fun fetchUltraShortTermForecast() {
        fetchUltraShortWithRetry("초단기 예보 스케줄")
    }

    private fun fetchUltraShortWithRetry(tag: String, attempt: Int = 1) {
        logger.info("[$tag][$attempt/$maxRetryAttempts] 호출 시작")

        val tmFc = getCurrentTmFcForUltraShort()
        val tmefs = generateUltraShortTermTmefs(tmFc)
        val varsList = listOf("T1H", "VEC", "WSD", "PTY", "RN1", "REH", "SKY")

        val requests = tmefs.flatMap { tmef ->
            varsList.map { vars ->
                weatherData.fetchUltraShortForecast(tmFc, tmef, vars)
                    .doOnSuccess {
                        logger.info("[$tag][$tmFc][$tmef][$vars] 호출 성공")
                    }
                    .doOnError { error ->
                        logger.error("[$tag][$tmFc][$tmef][$vars] 호출 실패: ${error.message}")
                    }
            }
        }

        Flux.fromIterable(requests)
            .parallel()
            .runOn(Schedulers.parallel())
            .flatMap { it }
            .sequential()
            .doOnComplete {
                logger.info("[$tag][$attempt] 초단기 예보 전체 병렬 호출 성공 완료")
            }
            .doOnError { error ->
                logger.error("[$tag][$attempt] 초단기 예보 병렬 호출 실패: ${error.message}")

                if (attempt < maxRetryAttempts) {
                    logger.warn("[$tag][$attempt] ${retryDelayMillis / 60000}분 후 재시도 예정")
                    retryFetch { fetchUltraShortWithRetry(tag, attempt + 1) }
                } else {
                    logger.error("[$tag] 최대 재시도 횟수 초과 → 중단")
                }
            }
            .subscribe()
    }

    private fun retryFetch(task: () -> Unit) {
        Timer().schedule(retryDelayMillis) {
            task()
        }
    }

    private fun getCurrentTmFcForUltraShort(): String {
        val now = LocalDateTime.now()
        val minute = (now.minute / 10) * 10
        val baseDateTime = now.withMinute(minute).withSecond(0).withNano(0)

        return baseDateTime.format(formatter)
    }

    private fun generateUltraShortTermTmefs(tmFc: String): List<String> {
        val baseDateTime = LocalDateTime.parse(tmFc, formatter)

        return (1..6).map { hourOffset ->
            baseDateTime.plusHours(hourOffset.toLong()).format(formatter)
        }
    }
}