package com.project.byeoldori.user.config

import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.ExternalServiceException
import com.project.byeoldori.common.exception.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.time.Duration

/**
 * 소셜 로그인 외부 호출은 시간 상한을 두고, 연결 실패·지연은 500 이 아니라 502 로 돌려준다.
 * (예전에는 타임아웃 없는 WebClient.create().block() 이라 트랜잭션 안에서 DB 커넥션을 무기한 쥐었다)
 */
class OAuthWebClientConfigTest {

    @Test
    fun `연결 실패는 502 외부 서비스 오류로 바꾼다`() {
        val err = WebClientRequestException(
            RuntimeException("connection refused"), HttpMethod.POST, URI("https://oauth.example/token"), HttpHeaders()
        )
        assertThatThrownBy { Mono.error<Map<*, *>>(err).blockForOAuth() }
            .isInstanceOf(ExternalServiceException::class.java)
            .extracting { (it as ExternalServiceException).errorCode.status }
            .isEqualTo(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `응답이 오지 않으면 상한 시간에 끊고 502`() {
        val started = System.nanoTime()
        assertThatThrownBy { Mono.never<Map<*, *>>().blockForOAuth(Duration.ofMillis(200)) }
            .isInstanceOf(ExternalServiceException::class.java)
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2))
    }

    @Test
    fun `제공자 오류 응답으로 만든 401 은 그대로 전달한다`() {
        assertThatThrownBy { Mono.error<Map<*, *>>(UnauthorizedException("OAuth 토큰 교환 실패")).blockForOAuth() }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `정상 응답은 그대로 돌려준다`() {
        assertThat(Mono.just(mapOf("access_token" to "t")).blockForOAuth()).containsEntry("access_token", "t")
    }

    @Test
    fun `전용 클라이언트는 연결만 받고 응답하지 않는 서버를 응답 타임아웃으로 끊는다`() {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { server ->
            val client = OAuthWebClientConfig().oauthWebClient()
            val started = System.nanoTime()

            assertThatThrownBy {
                client.get().uri("http://127.0.0.1:${server.localPort}/token")
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .blockForOAuth()
            }.isInstanceOf(ExternalServiceException::class.java)

            val elapsed = Duration.ofNanos(System.nanoTime() - started)
            assertThat(elapsed).isLessThan(OAuthWebClientConfig.CALL_TIMEOUT)
        }
    }

    @Test
    fun `타임아웃 값과 502 매핑이 유지된다`() {
        assertThat(OAuthWebClientConfig.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(3))
        assertThat(OAuthWebClientConfig.RESPONSE_TIMEOUT).isEqualTo(Duration.ofSeconds(5))
        assertThat(OAuthWebClientConfig.CALL_TIMEOUT).isGreaterThan(OAuthWebClientConfig.RESPONSE_TIMEOUT)
        assertThat(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.status).isEqualTo(HttpStatus.BAD_GATEWAY)
    }
}
