package com.project.byeoldori.forecast.api

import com.project.byeoldori.forecast.config.WeatherApiProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * 응답 도중 연결이 끊긴 요청(PrematureClose 등)만 한 번 더 보낸다.
 * 연결 단계 실패·HTTP 오류는 다시 보내지 않는다(기상청 호출을 늘리지 않게).
 */
class WeatherDataTransientRetryTest {

    private val calls = AtomicInteger()

    private fun requestError(cause: Throwable) =
        WebClientRequestException(cause, HttpMethod.GET, URI("https://apihub.kma.go.kr/x"), HttpHeaders())

    /** 호출 순서대로 failures 를 내고, 그 뒤로는 200 "ok" 를 돌려주는 WebClient */
    private fun weatherData(vararg failures: Throwable): WeatherData {
        val client = WebClient.builder()
            .baseUrl("https://apihub.kma.go.kr")
            .exchangeFunction { _ ->
                val n = calls.getAndIncrement()
                if (n < failures.size) Mono.error(failures[n])
                else Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build())
            }
            .build()
        return WeatherData(client, WeatherApiProperties("test-key")).also { it.transientRetryDelay = Duration.ofMillis(10) }
    }

    @Test
    fun `응답 도중 연결이 끊기면 한 번 더 보내 성공한다`() {
        val wd = weatherData(requestError(IOException("Connection prematurely closed BEFORE response")))

        assertThat(wd.fetchShortForecast("2026111505", "2026111506", "TMP").block()).isEqualTo("ok")
        assertThat(calls.get()).isEqualTo(2)

        calls.set(0)
        val wd2 = weatherData(requestError(IOException("Connection prematurely closed BEFORE response")))
        assertThat(wd2.fetchUltraShortForecast("202611150530", "202611150600", "T1H").block()).isEqualTo("ok")
        assertThat(calls.get()).isEqualTo(2)
    }

    @Test
    fun `두 번 연속 끊기면 2회에서 멈추고 실패를 그대로 돌려준다`() {
        val wd = weatherData(
            requestError(IOException("Connection prematurely closed BEFORE response")),
            requestError(IOException("Connection prematurely closed BEFORE response")),
        )

        val error = catchThrowable { wd.fetchShortForecast("2026111505", "2026111506", "TMP").block() }

        assertThat(error).isInstanceOf(WebClientRequestException::class.java)
        assertThat(calls.get()).isEqualTo(2)
    }

    @Test
    fun `연결 단계 실패와 HTTP 오류 상태는 다시 보내지 않는다`() {
        val connectFail = weatherData(requestError(ConnectException("Connection refused")))
        assertThat(catchThrowable { connectFail.fetchShortForecast("2026111505", "2026111506", "TMP").block() })
            .isInstanceOf(WebClientRequestException::class.java)
        assertThat(calls.get()).isEqualTo(1)

        calls.set(0)
        val httpError = weatherData(
            WebClientResponseException.create(503, "Service Unavailable", HttpHeaders.EMPTY, ByteArray(0), null)
        )
        assertThat(catchThrowable { httpError.fetchUltraShortForecast("202611150530", "202611150600", "T1H").block() })
            .isInstanceOf(WebClientResponseException::class.java)
        assertThat(calls.get()).isEqualTo(1)
    }

    @Test
    fun `응답 시간 초과는 일시적 끊김으로 보지 않는다`() {
        val timeout = requestError(io.netty.handler.timeout.ReadTimeoutException.INSTANCE)
        assertThat(KmaFailures.isTransientReadFailure(timeout)).isFalse()
        assertThat(KmaFailures.isTransientReadFailure(requestError(IOException("Connection reset by peer")))).isTrue()
    }
}
