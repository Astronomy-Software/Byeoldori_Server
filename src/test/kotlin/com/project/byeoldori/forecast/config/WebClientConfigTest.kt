package com.project.byeoldori.forecast.config

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration

/**
 * 기상청 API 가 오류 상태를 돌려줘도 authKey 가 예외 메시지·스택트레이스(→ 에러 로그·Loki)에 남지 않는지 확인하는 회귀 테스트.
 * 현재 Spring(6.2) 은 WebClientResponseException 메시지와 checkpoint 에서 쿼리를 빼고 찍는다.
 * 버전을 올리다 이 동작이 바뀌면 여기서 깨지므로, 그때 상태 핸들러로 키를 가려야 한다.
 */
class WebClientConfigTest {

    private val secret = "KMA-SECRET-KEY-1234"

    @Test
    fun `오류 응답 예외에 authKey 가 남지 않는다`() {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            val body = "quota exceeded".toByteArray()
            exchange.sendResponseHeaders(503, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val client = WebClientConfig().weatherApiClient()
            val error = catchThrowable {
                client.get()
                    .uri("http://127.0.0.1:${server.address.port}/cgi-bin/url/nph-dfs_vsrt_grd?tmfc=1&authKey=$secret")
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .block(Duration.ofSeconds(10))
            }

            assertThat(error).isInstanceOf(WebClientResponseException::class.java)
            assertThat((error as WebClientResponseException).statusCode.value()).isEqualTo(503)
            assertThat(error.message).doesNotContain(secret)

            // logger.error(msg, e) 가 찍는 전체 스택트레이스(suppressed·cause 포함)에도 없어야 한다
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            assertThat(trace).doesNotContain(secret)
        } finally {
            server.stop(0)
        }
    }
}
