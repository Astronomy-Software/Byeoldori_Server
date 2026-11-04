package com.project.byeoldori.forecast.scheduler

import com.project.byeoldori.forecast.api.KmaUnreachableException
import com.project.byeoldori.forecast.config.RetryProperties
import com.project.byeoldori.forecast.service.LiveGridForecastService
import com.project.byeoldori.forecast.service.MidCombinedForecastService
import com.project.byeoldori.forecast.service.MidForecastService
import com.project.byeoldori.forecast.service.MidTempForecastService
import com.project.byeoldori.forecast.service.ShortGridForecastService
import com.project.byeoldori.forecast.service.UltraGridForecastService
import com.project.byeoldori.forecast.snapshot.SnapshotRestore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.stubbing.Answer
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 기동 시 Redis 스냅샷이 현재 발표면 기상청 재적재를 건너뛰고, 아니면 기존처럼 받는다(복원은 기동 스레드 밖에서).
 * 연결 실패 재시도는 간격을 지수적으로 늘리고, 새 주기가 시작되면 이전 주기의 재시도는 버린다.
 */
class GridForecastSchedulerTest {

    private val ultra = mock(UltraGridForecastService::class.java)
    private val short = mock(ShortGridForecastService::class.java)
    private val live = mock(LiveGridForecastService::class.java)

    private fun scheduler(attempts: Int = 1) = GridForecastScheduler(
        ultra, short, live, RetryProperties(delay = 60, attempts = attempts),
        mock(MidForecastService::class.java), mock(MidTempForecastService::class.java), mock(MidCombinedForecastService::class.java)
    )

    private fun stubUpdates() {
        `when`(live.updateLiveData(anyString())).thenReturn(Mono.empty())
        `when`(ultra.updateAllUltraTMEFData(anyString(), anyList())).thenReturn(Mono.empty())
        `when`(short.updateAllShortTMEFData(anyString(), anyList())).thenReturn(Mono.empty())
    }

    @Test
    fun `스냅샷이 현재 발표(FRESH)면 기상청 적재를 건너뛴다`() {
        stubUpdates()
        `when`(live.restoreSnapshot(anyString())).thenReturn(SnapshotRestore.FRESH)
        `when`(ultra.restoreSnapshot(anyString())).thenReturn(SnapshotRestore.FRESH)
        `when`(short.restoreSnapshot(anyString())).thenReturn(SnapshotRestore.FRESH)

        scheduler().startupLoad().block()

        verify(live, never()).updateLiveData(anyString())
        verify(ultra, never()).updateAllUltraTMEFData(anyString(), anyList())
        verify(short, never()).updateAllShortTMEFData(anyString(), anyList())
    }

    @Test
    fun `스냅샷이 없거나(NONE) 낡았거나(STALE) 복원이 실패하면 기존처럼 기상청에서 받는다`() {
        stubUpdates()
        `when`(live.restoreSnapshot(anyString())).thenReturn(SnapshotRestore.NONE)
        `when`(ultra.restoreSnapshot(anyString())).thenReturn(SnapshotRestore.STALE)
        `when`(short.restoreSnapshot(anyString())).thenThrow(IllegalStateException("redis down"))

        scheduler().startupLoad().block()

        verify(live).updateLiveData(anyString())
        verify(ultra).updateAllUltraTMEFData(anyString(), anyList())
        verify(short).updateAllShortTMEFData(anyString(), anyList())
    }

    @Test
    fun `기동 이벤트 스레드는 스냅샷 복원(Redis)을 기다리지 않는다`() {
        stubUpdates()
        val release = CountDownLatch(1)
        val restoreAnswer = Answer<SnapshotRestore> { release.await(5, TimeUnit.SECONDS); SnapshotRestore.NONE }
        `when`(live.restoreSnapshot(anyString())).thenAnswer(restoreAnswer)
        `when`(ultra.restoreSnapshot(anyString())).thenReturn(SnapshotRestore.FRESH)
        `when`(short.restoreSnapshot(anyString())).thenReturn(SnapshotRestore.FRESH)

        val started = System.nanoTime()
        scheduler().initOnStartup()
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2))
        verify(live, never()).updateLiveData(anyString())

        // Redis 응답이 오면 이어서 복원·적재가 진행된다
        release.countDown()
        verify(live, timeout(5_000)).updateLiveData(anyString())
        verify(short, timeout(5_000)).restoreSnapshot(anyString())
        verify(short, never()).updateAllShortTMEFData(anyString(), anyList())
    }

    @Test
    fun `연결 실패는 재시도 간격을 지수적으로 늘리고, 그 밖의 실패는 고정 간격`() {
        val s = scheduler(attempts = 5)
        val unreachable = KmaUnreachableException("x")
        assertThat((1..4).map { s.retryDelayMillis(it, unreachable) }).containsExactly(60_000L, 120_000L, 240_000L, 480_000L)
        assertThat(s.retryDelayMillis(20, unreachable)).isEqualTo(30 * 60 * 1000L)
        assertThat(s.retryDelayMillis(3, IllegalStateException("parse"))).isEqualTo(60_000L)
    }

    @Test
    fun `새 주기가 시작되면 이전 주기의 재시도는 예약하지 않는다`() {
        val s = scheduler(attempts = 5)
        val old = s.nextGeneration("실황")
        s.nextGeneration("실황")  // 다음 cron 주기 시작

        assertThat(s.retry("실황", 1, old, { Mono.empty() }, KmaUnreachableException("x"))).isFalse()
    }

    @Test
    fun `최대 재시도 횟수를 넘으면 예약하지 않는다`() {
        val s = scheduler(attempts = 2)
        val gen = s.nextGeneration("단기예보")
        assertThat(s.retry("단기예보", 2, gen, { Mono.empty() }, null)).isFalse()
    }
}
