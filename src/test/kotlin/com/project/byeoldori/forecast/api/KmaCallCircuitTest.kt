package com.project.byeoldori.forecast.api

import com.project.byeoldori.forecast.service.ShortGridForecastService
import com.project.byeoldori.forecast.service.UltraGridForecastService
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotStore
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
import java.net.URI
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

    @Test
    fun `연결 단계 실패와 HTTP 오류 응답을 구분한다`() {
        val connect = WebClientRequestException(ConnectException("timed out"), HttpMethod.GET, URI("https://x"), HttpHeaders())
        assertThat(KmaFailures.isNetworkFailure(connect)).isTrue()
        assertThat(KmaFailures.isNetworkFailure(RuntimeException(ConnectException()))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(io.netty.channel.ConnectTimeoutException("t"))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(KmaUnreachableException("x"))).isTrue()
        assertThat(KmaFailures.isNetworkFailure(WebClientResponseException.create(503, "x", HttpHeaders(), ByteArray(0), null))).isFalse()
        assertThat(KmaFailures.isNetworkFailure(IllegalStateException("parse"))).isFalse()
        assertThat(KmaFailures.isNetworkFailure(null)).isFalse()
    }

    @Test
    fun `회로는 연속 실패에서만 열리고 성공하면 다시 센다`() {
        val c = KmaCallCircuit(3)
        c.onFailure(ConnectException()); c.onFailure(ConnectException()); c.onSuccess()
        c.onFailure(ConnectException()); c.onFailure(ConnectException())
        assertThat(c.isOpen).isFalse()
        c.onFailure(IllegalStateException("parse"))
        assertThat(c.isOpen).isFalse()
        c.onFailure(ConnectException())
        assertThat(c.isOpen).isTrue()
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
