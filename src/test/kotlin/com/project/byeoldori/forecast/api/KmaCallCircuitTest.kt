package com.project.byeoldori.forecast.api

import com.project.byeoldori.forecast.service.ShortGridForecastService
import com.project.byeoldori.forecast.service.UltraGridCellCodec
import com.project.byeoldori.forecast.service.UltraGridForecastService
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotKind
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
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 기상청 연결 실패 시 조기 중단: 연결 단계 실패가 이어지면 남은 tmef 를 두드리지 않고 이번 주기를 오류로 끝낸다.
 */
class KmaCallCircuitTest {

    private val tmefs = (0 until 70).map { "20261023%02d".format(it % 24) + it }

    private fun weatherData(calls: AtomicInteger, error: () -> Throwable): WeatherData =
        mock(WeatherData::class.java).also {
            `when`(it.fetchShortForecast(anyString(), anyString(), anyString())).thenAnswer {
                calls.incrementAndGet(); Mono.error<String>(error())
            }
            `when`(it.fetchUltraShortForecast(anyString(), anyString(), anyString())).thenAnswer {
                calls.incrementAndGet(); Mono.error<String>(error())
            }
        }

    private fun requestError(cause: Throwable) = WebClientRequestException(cause, HttpMethod.GET, URI("https://x"), HttpHeaders())

    private val readTimeout get() = requestError(io.netty.handler.timeout.ReadTimeoutException.INSTANCE)

    @Test
    fun `연결 단계 실패와 HTTP 오류 응답을 구분한다`() {
        assertThat(KmaFailures.isNetworkFailure(requestError(ConnectException("refused")))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(requestError(io.netty.channel.ConnectTimeoutException("t")))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(requestError(UnknownHostException("apihub.kma.go.kr")))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(requestError(NoRouteToHostException("x")))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(RuntimeException(ConnectException()))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(io.netty.channel.ConnectTimeoutException("t"))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(KmaUnreachableException("x"))).isTrue()
        // 연결은 됐고 응답만 느린 경우는 연결 실패가 아니다
        assertThat(KmaFailures.isNetworkFailure(readTimeout)).isFalse()
        assertThat(KmaFailures.isNetworkFailure(io.netty.handler.timeout.ReadTimeoutException.INSTANCE)).isFalse()
        assertThat(KmaFailures.isNetworkFailure(SocketTimeoutException("Read timed out"))).isFalse()
        assertThat(KmaFailures.isNetworkFailure(requestError(IllegalStateException("x")))).isFalse()
        assertThat(KmaFailures.isNetworkFailure(WebClientResponseException.create(503, "x", HttpHeaders(), ByteArray(0), null))).isFalse()
        assertThat(KmaFailures.isNetworkFailure(IllegalStateException("parse"))).isFalse()
        assertThat(KmaFailures.isNetworkFailure(null)).isFalse()
    }

    @Test
    fun `회로는 성공 전 연결 실패가 연속 3번이어야 열린다`() {
        val c = KmaCallCircuit(3)
        c.onFailure(ConnectException()); c.onFailure(ConnectException())
        assertThat(c.isOpen).isFalse()
        c.onFailure(IllegalStateException("parse"))
        c.onFailure(readTimeout)
        assertThat(c.isOpen).isFalse()
        c.onFailure(ConnectException())
        assertThat(c.isOpen).isTrue()
    }

    @Test
    fun `한 번이라도 성공한 주기에서는 연결 실패가 이어져도 회로가 열리지 않는다`() {
        val c = KmaCallCircuit(3)
        c.onSuccess()
        repeat(10) { c.onFailure(ConnectException()) }
        assertThat(c.isOpen).isFalse()

        // 성공 전에 열렸더라도 늦게 도착한 성공이 있으면 닫힌 것으로 본다(부분 결과 반영)
        val late = KmaCallCircuit(3)
        repeat(3) { late.onFailure(ConnectException()) }
        assertThat(late.isOpen).isTrue()
        late.onSuccess()
        assertThat(late.isOpen).isFalse()
    }

    @Test
    fun `응답 지연(ReadTimeout)만 이어지면 회로를 열지 않고 tmef 별로 건너뛴다`() {
        val calls = AtomicInteger()
        val svc = ShortGridForecastService(weatherData(calls) { readTimeout }, mock(ForecastSnapshotStore::class.java))

        svc.updateAllShortTMEFData("2026102320", tmefs.take(10)).block()

        assertThat(calls.get()).isEqualTo(10 * 11)
        assertThat(svc.getDataCount()).isZero()
    }

    @Test
    fun `일부 tmef 가 성공한 뒤 연결 시간 초과가 이어져도 부분 결과를 반영한다`() {
        val calls = AtomicInteger()
        val okBody = (0 until 149 * 2).joinToString(",") { "1.0" }
        val first = tmefs[0]
        val wd = mock(WeatherData::class.java).also {
            `when`(it.fetchUltraShortForecast(anyString(), anyString(), anyString())).thenAnswer { inv ->
                calls.incrementAndGet()
                if (inv.getArgument<String>(1) == first) Mono.just(okBody)
                else Mono.error(requestError(io.netty.channel.ConnectTimeoutException("connection timed out")))
            }
        }
        val store = ForecastSnapshotStore(ForecastSnapshotTest.MemoryBlobStore())
        val svc = UltraGridForecastService(wd, store)

        svc.updateAllUltraTMEFData("202610232030", tmefs.take(6)).block()

        // 성공한 tmef 가 있으므로 회로는 열리지 않고 남은 tmef 도 모두 시도한다
        assertThat(calls.get()).isEqualTo(6 * 7)
        assertThat(svc.getDataCount()).isEqualTo(1)
        // 부분 적재 스냅샷은 complete=false 로 남는다
        assertThat(store.load(ForecastSnapshotKind.ULTRA, UltraGridCellCodec)!!.complete).isFalse()
    }

    @Test
    fun `단기 - 연결 실패가 이어지면 남은 tmef 는 호출하지 않고 오류로 끝내며 기존 캐시는 유지한다`() {
        val calls = AtomicInteger()
        val svc = ShortGridForecastService(weatherData(calls) { ConnectException("connect timed out") }, mock(ForecastSnapshotStore::class.java))

        val error = catchThrowable { svc.updateAllShortTMEFData("2026102320", tmefs).block() }

        assertThat(error).isInstanceOf(KmaUnreachableException::class.java)
        assertThat(KmaFailures.isNetworkFailure(error)).isTrue()
        // 예전에는 70 tmef × 11 변수 = 770 번 시도. 동시 처리 5개 안에서 끊긴다.
        assertThat(calls.get()).isLessThanOrEqualTo(5 * 11)
        assertThat(svc.getDataCount()).isZero()
    }

    @Test
    fun `초단기 - 연결 실패가 이어지면 조기 중단`() {
        val calls = AtomicInteger()
        val svc = UltraGridForecastService(weatherData(calls) { ConnectException("refused") }, mock(ForecastSnapshotStore::class.java))

        val error = catchThrowable { svc.updateAllUltraTMEFData("202610232030", tmefs.take(6)).block() }

        assertThat(error).isInstanceOf(KmaUnreachableException::class.java)
        assertThat(calls.get()).isLessThanOrEqualTo(3 * 7)
    }

    @Test
    fun `네트워크가 아닌 실패(HTTP 오류)는 기존처럼 tmef 별로 건너뛰고 오류 없이 끝난다`() {
        val calls = AtomicInteger()
        val svc = ShortGridForecastService(
            weatherData(calls) { WebClientResponseException.create(500, "x", HttpHeaders(), ByteArray(0), null) },
            mock(ForecastSnapshotStore::class.java)
        )

        svc.updateAllShortTMEFData("2026102320", tmefs.take(10)).block()

        assertThat(calls.get()).isEqualTo(10 * 11)
        assertThat(svc.getDataCount()).isZero()
    }
}
