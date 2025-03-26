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
class ShortForecastScheduler(
    private val weatherData: WeatherData,
    private val retryProperties: RetryProperties
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

    private val retryDelayMillis = retryProperties.delay * 1000L
    private val maxRetryAttempts = retryProperties.attempts

    @Scheduled(cron = "0 0 2,5,8,11,14,17,20,23 * * *")
    fun fetchShortTermForecast() {
        fetchShortWithRetry("단기 예보 스케줄")
    }

    private fun fetchShortWithRetry(tag: String, attempt: Int = 1) {
        logger.info("[$tag][$attempt/$maxRetryAttempts] 호출 시작")

        val tmFc = getCurrentTmFcForShort()
        val tmefs = generateShortTermTmefsAfterPolicyChange(tmFc)
        val varsList = listOf("TMP", "TMX", "TMN", "VEC", "WSD", "SKY", "PTY", "POP", "PCP", "SNO", "REH")

        val requests = tmefs.flatMap { tmef ->
            varsList.map { vars ->
                weatherData.fetchShortForecast(tmFc, tmef, vars)
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
                logger.info("[$tag][$attempt] 단기 예보 전체 병렬 호출 성공 완료")
            }
            .doOnError { error ->
                logger.error("[$tag][$attempt] 단기 예보 병렬 호출 실패: ${error.message}")

                if (attempt < maxRetryAttempts) {
                    logger.warn("[$tag][$attempt] ${retryDelayMillis / 60000}분 후 재시도 예정")
                    retryFetch { fetchShortWithRetry(tag, attempt + 1) }
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

    private fun getCurrentTmFcForShort(): String {
        val now = LocalDateTime.now()

        val baseHours = listOf(2, 5, 8, 11, 14, 17, 20, 23)
        val nearestBaseHour = baseHours.lastOrNull { it <= now.hour } ?: 23

        val baseDate = if (nearestBaseHour > now.hour) now.minusDays(1) else now
        val tmFcDateTime = baseDate.withHour(nearestBaseHour).withMinute(0).withSecond(0).withNano(0)

        return tmFcDateTime.format(formatter)
    }

    private fun generateShortTermTmefsAfterPolicyChange(tmFc: String): List<String> {
        val baseDateTime = LocalDateTime.parse(tmFc, formatter)
        val baseHour = baseDateTime.hour

        val endDate = when (baseHour) {
            2, 5, 8, 11, 14 -> baseDateTime.toLocalDate().plusDays(2) // 글피 자정
            17, 20, 23      -> baseDateTime.toLocalDate().plusDays(3) // 그글피 자정
            else -> throw IllegalArgumentException("발표시간이 잘못되었습니다. ($baseHour)")
        }

        val endDateTime = endDate.atStartOfDay()

        val times = mutableListOf<LocalDateTime>()
        var current = baseDateTime

        while (!current.isAfter(endDateTime)) {
            times.add(current)
            current = current.plusHours(1)
        }

        return times.map { it.format(formatter) }
    }
}