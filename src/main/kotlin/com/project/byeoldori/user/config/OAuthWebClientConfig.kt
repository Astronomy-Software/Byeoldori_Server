package com.project.byeoldori.user.config

import com.project.byeoldori.common.exception.ExternalServiceException
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.TimeoutException as NettyTimeoutException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * 소셜 로그인(Google·Kakao·Naver) 토큰 교환·사용자 정보 조회 전용 WebClient.
 *
 * 예전에는 WebClient.create() 로 타임아웃 없는 클라이언트를 만들어 block() 으로 기다렸다. 로그인 메서드가
 * @Transactional 이라 그동안 DB 커넥션을 쥐고 있어, 제공자가 응답을 끌면 커넥션 풀(기본 10개)이 바닥나
 * DB 를 쓰는 모든 API 가 함께 멈췄다. 연결·응답 시간과 호출 전체 시간에 상한을 둔다.
 */
@Configuration
class OAuthWebClientConfig {

    @Bean
    fun oauthWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT.toMillis().toInt())
            .responseTimeout(RESPONSE_TIMEOUT)

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(5)

        /** 호출 한 번(연결·요청·응답 본문까지)의 전체 상한. */
        val CALL_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}

/**
 * 소셜 제공자 호출을 시간 상한을 두고 기다린다.
 * 연결 실패·응답 지연(WebClientRequestException, 본문 수신 중 ReadTimeout)과 전체 시간 초과는 500 이 아니라 502 로 돌려준다.
 * 제공자의 4xx·5xx 응답은 호출부 onStatus 에서 만든 예외(401 등)를 그대로 던진다.
 */
fun <T : Any> Mono<T>.blockForOAuth(timeout: Duration = OAuthWebClientConfig.CALL_TIMEOUT): T? =
    this.timeout(timeout)
        .onErrorMap({ it is WebClientRequestException || it is TimeoutException || it is NettyTimeoutException }) {
            ExternalServiceException()
        }
        // timeout 연산자가 먼저 끊으므로 여기 상한은 안전망이다
        .block(timeout.plusSeconds(1))
