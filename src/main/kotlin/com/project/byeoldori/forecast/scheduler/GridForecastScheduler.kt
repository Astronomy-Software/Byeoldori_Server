package com.project.byeoldori.forecast.scheduler

import com.project.byeoldori.forecast.api.KmaFailures
import com.project.byeoldori.forecast.config.RetryProperties
import com.project.byeoldori.forecast.service.*
import com.project.byeoldori.forecast.snapshot.SnapshotRestore
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil.getTMEFTimesForShortForecast
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil.getTMFCTimeForShort
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.schedule

@Component
class GridForecastScheduler(
    private val ultraGridForecastService: UltraGridForecastService,
    private val shortGridForecastService: ShortGridForecastService,
    private val liveGridForecastService: LiveGridForecastService,
    private val retryProperties: RetryProperties,
    private val midForecastService: MidForecastService,
    private val midTempForecastService: MidTempForecastService,
    private val midCombinedForecastService: MidCombinedForecastService

    ) {
    // 단 , 이파일에서 getTMFCTimeUltra 이런친구들은 외부로 보내도됨.
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val maxRetryAttempts = retryProperties.attempts
    private val retryDelayMillis = retryProperties.delay * 1000L

    // 태그별 적재 주기 번호. 새 주기가 시작되면 이전 주기의 남은 재시도는 버린다(지수 간격이 cron 간격보다 길어져도 겹치지 않게).
    private val generations = ConcurrentHashMap<String, AtomicLong>()

    // 서버 시작 시 실황/초단기/단기 데이터 즉시 로드 (메모리 초기화)
    // 배포마다 기상청에서 전부 다시 받지 않도록, Redis 스냅샷이 같은 발표(tmfc)면 그것을 올리고 기상청 호출을 건너뛴다.
    // Redis GET·gzip 해제는 boundedElastic 에서 해, Redis 가 죽어 있어도 기동 이벤트 스레드를 Lettuce 시간 초과만큼 붙잡지 않는다.
    // 복원·적재가 끝나기 전에는 메모리가 비어 있어 warm 헬스가 WARMING 이므로 블루그린 전환은 그대로 적재를 기다린다.
    @EventListener(ApplicationReadyEvent::class)
    fun initOnStartup() {
        logger.info("[시작] 서버 기동 후 실황/초단기/단기 예보 즉시 로드 시작")
        startupLoad()
            .doOnError { e -> logger.error("[시작] 예보 초기 로드 실패", e) }
            .onErrorResume { Mono.empty() }
            .subscribe()
    }

    fun startupLoad(): Mono<Void> =
        Mono.fromRunnable<Void> {
            restoreOrFetch("실황", { liveGridForecastService.restoreSnapshot(ForecastTimeUtil.getStableLiveTmfc()) }) { clockForLiveForecast() }
            restoreOrFetch("초단기예보", { ultraGridForecastService.restoreSnapshot(ForecastTimeUtil.getStableUltraTmfc()) }) { clockForUltraForecast() }
            restoreOrFetch("단기예보", { shortGridForecastService.restoreSnapshot(getTMFCTimeForShort()) }) { clockForShortForecast() }
            catchUpMidForecastIfStale()
        }.subscribeOn(Schedulers.boundedElastic())

    // 중기 예보는 하루 두 번(06:10·18:10)만 받아, 수집이 며칠 실패한 뒤 고쳐 배포해도 다음 정기 시각까지 묵은 값이 남는다.
    // 기동 때 24시간 안에 저장한 묶음이 없으면 한 번 바로 받는다(정상일 땐 DB 조회 1회뿐, 기상청 호출 없음).
    private fun catchUpMidForecastIfStale() {
        val fresh = try {
            midCombinedForecastService.hasFreshData()
        } catch (e: Exception) {
            logger.warn("[시작] 중기 예보 최신 여부 확인 실패, 보충 수집은 건너뜁니다: ${e.message}")
            return
        }
        if (fresh) return
        logger.info("[시작] 최근 24시간 안에 저장한 중기 예보가 없어 지금 바로 받습니다")
        collectMidCombinedDirect()
    }

    private fun restoreOrFetch(tag: String, restore: () -> SnapshotRestore, fetch: () -> Unit) {
        val result = try {
            restore()
        } catch (e: Exception) {
            logger.warn("[$tag] 스냅샷 복원 실패, 기상청에서 받습니다: ${e.message}")
            SnapshotRestore.NONE
        }
        when (result) {
            SnapshotRestore.FRESH -> logger.info("[$tag] Redis 스냅샷이 현재 발표와 같아 기상청 재적재를 건너뜁니다 (다음 정기 갱신에서 새로 받음)")
            SnapshotRestore.STALE -> {
                logger.info("[$tag] 이전 발표 스냅샷을 대체값으로 올리고 기상청에서 새로 받습니다")
                fetch()
            }
            SnapshotRestore.NONE -> fetch()
        }
    }

    // 매 10분 + 1분 간격으로 실황 데이터 갱신
    @Scheduled(cron = "0 1/10 * * * *", zone = "Asia/Seoul")
    fun clockForLiveForecast() {
        logger.info("실황 데이터 받아오는중...")
        fetchWithRetry(
            tag = "실황",
            fetchFunction = {
                val tmfc = ForecastTimeUtil.getStableLiveTmfc()
                liveGridForecastService.updateLiveData(tmfc)
            }
        )
    }

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
            }
        )
    }

    // 중기 예보(06시·18시 발표) 수집·병합·저장. 발표 직후(06:10·18:10)에 받으면 기상청에 아직 당일 발표분이 없어
    // 항상 직전 발표분만 받았다 → 30분 여유를 두고 06:40·18:40 에 받고, 그래도 당일 발표분이 없으면 1시간 뒤(07:40·19:40) 한 번 더 받는다.
    // 당일 발표분이 이미 있으면(07:40·19:40) 기상청을 부르지 않는다. 끝내 없으면 직전 발표분을 그대로 쓴다.
    @Scheduled(cron = "0 40 6,7,18,19 * * *", zone = "Asia/Seoul")
    fun collectMidCombinedScheduled() {
        val expected = ForecastTimeUtil.getExpectedMidTmfc()
        val already = try {
            midCombinedForecastService.hasIssue(expected)
        } catch (e: Exception) {
            logger.warn("[중기예보] 당일 발표분(tmFc=$expected) 저장 여부 확인 실패, 수집은 그대로 진행: ${e.message}")
            false
        }
        if (already) {
            logger.info("[중기예보] 당일 발표분(tmFc=$expected)이 이미 있어 수집을 건너뜁니다")
            return
        }
        collectMidCombinedDirect()
    }

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

    // 정리는 수집(06:40·18:40, 재시도 최대 5분)이 끝난 뒤에 돈다. 수집보다 먼저 돌면 새 묶음이 없는 상태에서 정리하게 된다.
    // 06:50 / 18:50 — 24시간 지난 중기 육상 예보 삭제
    @Scheduled(cron = "0 50 6,18 * * *", zone = "Asia/Seoul")
    fun deleteOldMidForecasts() {
        logger.info("24시간 지난 중기 육상 데이터 삭제 시작")
        midForecastService.deleteOldForecasts()
    }

    // 06:51 / 18:51 — 24시간 지난 중기 기온 예보 삭제
    @Scheduled(cron = "0 51 6,18 * * *", zone = "Asia/Seoul")
    fun deleteOldMidTempForecasts() {
        logger.info("24시간 지난 중기 기온 데이터 삭제 시작")
        midTempForecastService.deleteOldForecasts()
    }

    // 06:55 / 18:55 — 24시간 지난 중기 통합 예보 삭제(새 묶음이 없으면 지우지 않음, MidCombinedForecastService.deleteOldForecasts)
    @Scheduled(cron = "0 55 6,18 * * *", zone = "Asia/Seoul")
    fun deleteOldMidCombinedForecasts() {
        logger.info("24시간 지난 중기 병합 예보 삭제 시작")
        midCombinedForecastService.deleteOldForecasts()
    }

    // 공통 재시도 fetch 처리 (attempt=1 이면 새 주기를 시작한다)
    fun fetchWithRetry(
        tag: String,
        attempt: Int = 1,
        fetchFunction: () -> Mono<Void>,
    ) {
        val generation = if (attempt == 1) nextGeneration(tag) else currentGeneration(tag)
        runAttempt(tag, attempt, generation, fetchFunction)
    }

    private fun runAttempt(tag: String, attempt: Int, generation: Long, fetchFunction: () -> Mono<Void>) {
        logger.info("[$tag][$attempt/$maxRetryAttempts] 실행 시도 중...")
        try {
            // 실패는 subscribe 의 에러 콜백에서 받는다. doOnError + 인자 없는 subscribe() 는 에러를 처리하지 않은 것으로 보여
            // Reactor 가 "Operator called default onErrorDropped" ERROR 를 한 번 더 찍었다(기상청 504 한 번에 ERROR 3건).
            // 재시도로 회복될 수 있는 실패는 WARN 한 줄, 끝내 실패하면 retry() 가 ERROR 를 남긴다.
            fetchFunction().subscribe(
                null,
                { e ->
                    logger.warn("[$tag][$attempt] 오류 발생: ${KmaFailures.describe(e)}")
                    retry(tag, attempt, generation, fetchFunction, e)
                }
            )
        } catch (e: Exception) {
            logger.warn("[$tag][$attempt] 예외 발생: ${KmaFailures.describe(e)}")
            retry(tag, attempt, generation, fetchFunction, e)
        }
    }

    /** 재시도를 예약했으면 true. 최대 횟수를 넘었거나 같은 태그의 새 주기가 이미 시작됐으면 false. */
    fun retry(tag: String, currentAttempt: Int, generation: Long, fetchFunction: () -> Mono<Void>, error: Throwable?): Boolean {
        if (currentAttempt >= maxRetryAttempts) {
            val cause = error?.let { ": ${KmaFailures.describe(it)}" } ?: ""
            logger.error("[$tag] 최대 재시도 횟수($maxRetryAttempts) 초과 → 중단$cause", error)
            return false
        }
        if (generation != currentGeneration(tag)) {
            logger.info("[$tag][$currentAttempt] 새 주기가 이미 시작돼 이전 주기 재시도를 중단합니다")
            return false
        }
        val delay = retryDelayMillis(currentAttempt, error)
        logger.info("[$tag][$currentAttempt] ${delay / 1000}초 후 재시도 예정")
        Timer().schedule(delay) {
            if (generation == currentGeneration(tag)) {
                runAttempt(tag, currentAttempt + 1, generation, fetchFunction)
            }
        }
        return true
    }

    /**
     * 기상청에 연결조차 못 한 실패(연결 거부·시간 초과·회로 차단)는 간격을 지수적으로 늘린다(기본 60s → 120s → 240s …, 상한 30분).
     * 그 밖의 실패는 기존처럼 고정 간격.
     */
    fun retryDelayMillis(currentAttempt: Int, error: Throwable?): Long {
        if (!KmaFailures.isNetworkFailure(error)) return retryDelayMillis
        val shift = (currentAttempt - 1).coerceIn(0, 10)
        return (retryDelayMillis shl shift).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
    }

    fun nextGeneration(tag: String): Long = generations.computeIfAbsent(tag) { AtomicLong() }.incrementAndGet()

    fun currentGeneration(tag: String): Long = generations[tag]?.get() ?: 0L

    companion object {
        private const val MAX_RETRY_DELAY_MILLIS = 30 * 60 * 1000L
    }
}