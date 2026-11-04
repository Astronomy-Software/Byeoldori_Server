package com.project.byeoldori.forecast.api

import com.project.byeoldori.forecast.config.RetryProperties
import com.project.byeoldori.forecast.scheduler.GridForecastScheduler
import com.project.byeoldori.forecast.service.LiveGridForecastService
import com.project.byeoldori.forecast.service.MidCombinedForecastService
import com.project.byeoldori.forecast.service.MidForecastService
import com.project.byeoldori.forecast.service.MidTempForecastService
import com.project.byeoldori.forecast.service.ShortGridForecastService
import com.project.byeoldori.forecast.service.UltraGridForecastService
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotStore
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * half-open 프로브: 직전 주기가 기상청 연결 실패로 끝났으면 다음 주기는 요청 1건으로 먼저 떠보고,
 * 그것도 연결 단계에서 실패하면 나머지 요청 없이 끝낸다(VM IP 가 막힌 동안 주기당 1건).
 */
class KmaReachabilityTest {

    private enum class Mode { OK, CONNECT_FAIL, READ_TIMEOUT }

    private val mode = AtomicReference(Mode.OK)
    private val calls = AtomicInteger()
    private val reachability = KmaReachability()
    private val store = ForecastSnapshotStore(ForecastSnapshotTest.MemoryBlobStore())

    private val okBody = (0 until 149 * 2).joinToString(",") { "1.0" }
    private val tmefs = (0 until 6).map { "2026102321%02d".format(it * 10) }

    private fun requestError(cause: Throwable) = WebClientRequestException(cause, HttpMethod.GET, URI("https://x"), HttpHeaders())

    private fun respond(): Mono<String> {
        calls.incrementAndGet()
        return when (mode.get()!!) {
            Mode.OK -> Mono.just(okBody)
            Mode.CONNECT_FAIL -> Mono.error(requestError(io.netty.channel.ConnectTimeoutException("connection timed out")))
            Mode.READ_TIMEOUT -> Mono.error(requestError(io.netty.handler.timeout.ReadTimeoutException.INSTANCE))
        }
    }

    private val weatherData: WeatherData = mock(WeatherData::class.java).also {
        `when`(it.fetchUltraShortForecast(anyString(), anyString(), anyString())).thenAnswer { respond() }
        `when`(it.fetchShortForecast(anyString(), anyString(), anyString())).thenAnswer { respond() }
        `when`(it.fetchLiveWeather(anyString(), anyString())).thenAnswer { respond() }
    }

    private val ultra = UltraGridForecastService(weatherData, store, reachability)
    private val short = ShortGridForecastService(weatherData, store, reachability)
    private val live = LiveGridForecastService(weatherData, store, reachability)

    private fun ultraCycle(): Throwable? = catchThrowable { ultra.updateAllUltraTMEFData("202610232030", tmefs).block() }

    /** 정상 적재로 캐시를 채운 뒤 연결 실패 주기를 한 번 겪어 막힌 상태로 만든다. */
    private fun warmThenBlock() {
        mode.set(Mode.OK)
        assertThat(ultraCycle()).isNull()
        assertThat(ultra.getDataCount()).isEqualTo(6)
        mode.set(Mode.CONNECT_FAIL)
        assertThat(ultraCycle()).isInstanceOf(KmaUnreachableException::class.java)
        assertThat(reachability.isUnreachable).isTrue()
        calls.set(0)
    }

    @Test
    fun `막힌 뒤 다음 주기는 프로브 1건만 보내고 실패하면 바로 끝내며 기존 캐시를 유지한다`() {
        warmThenBlock()

        repeat(3) {
            val error = ultraCycle()
            assertThat(error).isInstanceOf(KmaUnreachableException::class.java)
            // 스케줄러가 연결 실패로 보고 지수 간격으로 재시도하도록 분류된다
            assertThat(KmaFailures.isNetworkFailure(error)).isTrue()
        }

        assertThat(calls.get()).isEqualTo(3)  // 주기당 1건
        assertThat(ultra.getDataCount()).isEqualTo(6)
        assertThat(reachability.isUnreachable).isTrue()

        // 상태는 서비스 공용: 단기·실황도 주기당 1건으로 끝난다
        calls.set(0)
        assertThat(catchThrowable { short.updateAllShortTMEFData("2026102320", tmefs.map { it.take(10) }).block() })
            .isInstanceOf(KmaUnreachableException::class.java)
        assertThat(catchThrowable { live.updateLiveData("202610232030").block() })
            .isInstanceOf(KmaUnreachableException::class.java)
        assertThat(calls.get()).isEqualTo(2)
    }

    @Test
    fun `프로브가 성공하면 상태를 지우고 평소처럼 모든 요청을 보낸다`() {
        warmThenBlock()
        mode.set(Mode.OK)

        assertThat(ultraCycle()).isNull()

        assertThat(calls.get()).isEqualTo(1 + 6 * 7)  // 프로브 1건 + 평소 주기
        assertThat(reachability.isUnreachable).isFalse()
        assertThat(ultra.getDataCount()).isEqualTo(6)

        // 다음 주기부터는 프로브 없이
        calls.set(0)
        assertThat(ultraCycle()).isNull()
        assertThat(calls.get()).isEqualTo(6 * 7)
    }

    @Test
    fun `다른 서비스에서 기상청 응답을 받으면 상태가 지워진다`() {
        warmThenBlock()
        mode.set(Mode.OK)

        // 실황은 막힌 상태라 프로브 1건 + 평소 7건, 성공으로 상태를 지운다
        live.updateLiveData("202610232030").block()
        assertThat(calls.get()).isEqualTo(1 + 7)
        assertThat(reachability.isUnreachable).isFalse()

        calls.set(0)
        assertThat(ultraCycle()).isNull()
        assertThat(calls.get()).isEqualTo(6 * 7)
    }

    @Test
    fun `상태가 비어 있으면 프로브 없이 기존과 같다`() {
        assertThat(reachability.isUnreachable).isFalse()

        assertThat(ultraCycle()).isNull()
        assertThat(calls.get()).isEqualTo(6 * 7)
        assertThat(ultra.getDataCount()).isEqualTo(6)

        calls.set(0)
        live.updateLiveData("202610232030").block()
        assertThat(calls.get()).isEqualTo(7)
        assertThat(reachability.isUnreachable).isFalse()
    }

    @Test
    fun `응답 지연(ReadTimeout)은 막힌 상태로 표시하지 않는다`() {
        mode.set(Mode.READ_TIMEOUT)

        assertThat(ultraCycle()).isNull()  // tmef 별로 건너뛰고 오류 없이 끝남
        assertThat(catchThrowable { live.updateLiveData("202610232030").block() }).isNotNull()
        assertThat(reachability.isUnreachable).isFalse()

        // 다음 주기도 프로브 없이 평소대로
        calls.set(0)
        assertThat(ultraCycle()).isNull()
        assertThat(calls.get()).isEqualTo(6 * 7)
    }

    @Test
    fun `막힌 상태에서 프로브가 응답 지연이면 기상청은 살아 있으므로 상태를 지우고 평소대로 진행한다`() {
        warmThenBlock()
        mode.set(Mode.READ_TIMEOUT)

        assertThat(ultraCycle()).isNull()

        assertThat(calls.get()).isEqualTo(1 + 6 * 7)
        assertThat(reachability.isUnreachable).isFalse()
        assertThat(ultra.getDataCount()).isEqualTo(6)  // 결과가 비어 기존 캐시 유지
    }

    @Test
    fun `프로브 실패로 끝난 주기의 재시도도 지수 간격을 따른다`() {
        warmThenBlock()
        val error = ultraCycle()

        val scheduler = GridForecastScheduler(
            ultra, short, live, RetryProperties(delay = 60, attempts = 5),
            mock(MidForecastService::class.java), mock(MidTempForecastService::class.java), mock(MidCombinedForecastService::class.java)
        )
        assertThat((1..4).map { scheduler.retryDelayMillis(it, error) }).containsExactly(60_000L, 120_000L, 240_000L, 480_000L)
    }
}
