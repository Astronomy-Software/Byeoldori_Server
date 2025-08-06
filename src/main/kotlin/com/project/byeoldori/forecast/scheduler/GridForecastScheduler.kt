package com.project.byeoldori.forecast.scheduler

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.config.RetryProperties
import com.project.byeoldori.forecast.service.*
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil
import com.project.byeoldori.forecast.utils.forecasts.MidForecastParser
import com.project.byeoldori.forecast.utils.forecasts.MidTempForecastParser
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.schedule

@Component
class GridForecastScheduler(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val retryProperties: RetryProperties,
    private val weatherData: WeatherData,
    private val midForecastParser: MidForecastParser,
    private val midForecastService: MidForecastService,
    private val midTempForecastParser: MidTempForecastParser,
    private val midTempForecastService: MidTempForecastService,
    private val midCombinedForecastService: MidCombinedForecastService

    ) {
    // 단 , 이파일에서 getTMFCTimeUltra 이런친구들은 외부로 보내도됨.
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val maxRetryAttempts = retryProperties.attempts
    private val retryDelayMillis = retryProperties.delay * 1000L

    // 특정시각마다 업데이트가 많이 느릴때가있음. 시간을 잘 조정해봐야할것으로 보임.
    // 매시각 10분마다 실행 -> 초단기예보 및 실황 , 일단 실황은 제외
    @Scheduled(cron = "0 0/10 * * * *")
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
    @Scheduled(cron = "0 0 2/3 * * *")
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

    // 06시, 18시 실행 -> 중기 육상 예보
    @Scheduled(cron = "0 0 6,18 * * *")
    fun clockForMidForecast() {
        logger.info("중기 육상 예보 받아오는중...")

        fetchWithRetry(
            tag = "중기육상",
            fetchFunction = {
                weatherData.fetchMidLandForecast().flatMap { response ->
                    val parsed = midForecastParser.parse(response)
                    if (parsed.isEmpty()) Mono.empty() else {
                        midForecastService.saveAll(parsed)
                        Mono.empty()
                    }
                }
            }
        )
    }

    // 06시, 18시 1분 실행, 중기 육상 스케줄러와 겹치지 않기 위해 -> 중기 기온
    @Scheduled(cron = "0 1 6,18 * * *")
    fun clockForMidTempForecast() {
        logger.info("중기 기온 예보 받아오는중...")

        fetchWithRetry(
            tag = "중기기온",
            fetchFunction = {
                weatherData.fetchMidTemperatureForecast().flatMap { response ->
                    val parsed = midTempForecastParser.parse(response)
                    if (parsed.isEmpty()) Mono.empty() else {
                        midTempForecastService.saveAll(parsed)
                        Mono.empty()
                    }
                }
            }
        )
    }

    // 중기 육상,기온 예보를 자동 병합 후 저장하는 스케줄러 추가
    @Scheduled(cron = "0 7 6,18 * * *")
    fun clockForMidCombinedForecast() {
        logger.info("중기 병합 예보 병합 및 저장 중...")

        val landList = midForecastService.findAllEntity()
        val tempList = midTempForecastService.findAllEntity()
        val combinedList = midCombinedForecastService.mergeForecasts(landList, tempList)
        midCombinedForecastService.saveAll(combinedList)
    }

    // 매 정각마다 24시간 지난 중기 육상 예보 삭제
    @Scheduled(cron = "0 0 * * * *")
    fun deleteOldMidForecasts() {
        logger.info("24시간 지난 중기 육상 데이터 삭제 시작")
        midForecastService.deleteOldForecasts()
    }

    // 매 정각마다 24시간 지난 중기 기온 예보 삭제
    @Scheduled(cron = "0 0 * * * *")
    fun deleteOldMidTempForecasts() {
        logger.info("24시간 지난 중기 기온 데이터 삭제 시작")
        midTempForecastService.deleteOldForecasts()
    }

    // 매 정각마다 24시간 지난 중기 통합 예보 삭제
    @Scheduled(cron = "0 0 * * * *")
    fun deleteOldMidCombinedForecasts() {
        logger.info("24시간 지난 중기 병합 예보 삭제 시작")
        midCombinedForecastService.deleteOldForecasts()
    }


    fun getTMFCTimeUltra(): String {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

        // 1) 현재 분을 10분 단위로 내림(floor) 처리
        //    예) 07분 -> 00분, 17분 -> 10분, 25분 -> 20분
        val flooredMinutes = (current.minute / 10) * 10

        val dateTime = current
            .withMinute(flooredMinutes)
            .withSecond(0)
            .withNano(0)

        // 2) 포맷팅하여 반환
        return dateTime.format(formatter)
    }

    fun getTMFCTimeForShort(): String {
        val current = LocalDateTime.now()
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
        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

        // 분/초/나노초를 0으로 맞춘 정각 시간
        val flooredTime = now.withMinute(0).withSecond(0).withNano(0)

        // 만약 now.minute == 0 이면 정각이므로 offsets = 0..5
        // 아니라면 offsets = 1..6 (즉, 다음 정각부터 시작)
        val offsets = if (now.minute == 0) {
            listOf(0L, 1L, 2L, 3L, 4L, 5L)
        } else {
            listOf(1L, 2L, 3L, 4L, 5L, 6L)
        }

        return offsets.map { offset ->
            val dateTime = flooredTime.plusHours(offset)
            dateTime.format(formatter)
        }
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
        // 4) 6시간 후부터 리스트 생성 (초단기예보와 겹치지 않게)
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